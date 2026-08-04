package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.config.SecurityProperties;
import com.fantasy.bff.dto.request.GoogleCodeLoginRequest;
import com.fantasy.bff.dto.request.LoginRequest;
import com.fantasy.bff.dto.request.RefreshRequest;
import com.fantasy.bff.dto.response.AuthResponse;
import com.fantasy.bff.email.EmailVerificationEmailSender;
import com.fantasy.bff.email.PasswordResetEmailSender;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.security.FacebookTokenVerifier;
import com.fantasy.bff.security.GoogleCodeExchanger;
import com.fantasy.bff.security.GoogleIdentity;
import com.fantasy.bff.security.GoogleTokenVerifier;
import com.fantasy.bff.security.JwtTokenValidator;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private DatabaseServiceClient databaseServiceClient;
    private JwtTokenValidator jwtTokenValidator;
    private PasswordEncoder passwordEncoder;
    private GoogleTokenVerifier googleTokenVerifier;
    private GoogleCodeExchanger googleCodeExchanger;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        databaseServiceClient = mock(DatabaseServiceClient.class);
        jwtTokenValidator = mock(JwtTokenValidator.class);
        passwordEncoder = mock(PasswordEncoder.class);
        googleTokenVerifier = mock(GoogleTokenVerifier.class);
        googleCodeExchanger = mock(GoogleCodeExchanger.class);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$dummyDummyDummyDummyDummyDummyDummyDummyDummyDummyDu");
        authService = new AuthService(
                databaseServiceClient,
                jwtTokenValidator,
                passwordEncoder,
                googleTokenVerifier,
                googleCodeExchanger,
                mock(FacebookTokenVerifier.class),
                mock(PasswordResetEmailSender.class),
                mock(EmailVerificationEmailSender.class),
                new SecurityProperties(List.of(), List.of(), List.of()),
                "http://localhost:4200");
    }

    @Test
    void login_withUnknownEmail_stillRunsPasswordComparison_toEqualizeTiming() {
        when(databaseServiceClient.findUserByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "guess123")))
                .isInstanceOf(SecurityException.class);

        // The fix: even for a non-existent account a bcrypt comparison runs (against the dummy
        // hash), so the response time can't reveal that the email is unregistered.
        verify(passwordEncoder).matches(eq("guess123"), anyString());
    }

    @Test
    void login_withPasswordlessSocialUser_stillRunsPasswordComparison() {
        User socialUser = new User("user-1", "social@example.com", null, 0, true);
        when(databaseServiceClient.findUserByEmail("social@example.com"))
                .thenReturn(Optional.of(socialUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("social@example.com", "guess123")))
                .isInstanceOf(SecurityException.class);

        verify(passwordEncoder).matches(eq("guess123"), anyString());
    }

    @Test
    void refresh_withRevokedTokenVersion_isRejected() {
        // The refresh token still carries version 0, but the account has since bumped to 1
        // (e.g. a password reset), so the stale token must be rejected.
        Claims claims = mock(Claims.class);
        when(claims.get("email", String.class)).thenReturn("user@example.com");
        when(jwtTokenValidator.validateAndExtractRefreshTokenClaims("stale-refresh")).thenReturn(claims);
        when(jwtTokenValidator.getTokenVersion(claims)).thenReturn(Optional.of(0));
        when(databaseServiceClient.findUserByEmail("user@example.com"))
                .thenReturn(Optional.of(new User("user-1", "user@example.com", "hash", 1, true)));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("stale-refresh")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void refresh_withCurrentTokenVersion_issuesNewTokens() {
        Claims claims = mock(Claims.class);
        when(claims.get("email", String.class)).thenReturn("user@example.com");
        when(jwtTokenValidator.validateAndExtractRefreshTokenClaims("good-refresh")).thenReturn(claims);
        when(jwtTokenValidator.getTokenVersion(claims)).thenReturn(Optional.of(3));
        when(databaseServiceClient.findUserByEmail("user@example.com"))
                .thenReturn(Optional.of(new User("user-1", "user@example.com", "hash", 3, true)));
        when(jwtTokenValidator.generateToken(anyString(), anyString(), anyBoolean())).thenReturn("new-access");
        when(jwtTokenValidator.generateRefreshToken(anyString(), anyString(), anyInt())).thenReturn("new-refresh");

        AuthResponse response = authService.refresh(new RefreshRequest("good-refresh"));

        assertThat(response.token()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        // The new refresh token is re-stamped with the account's current version.
        verify(jwtTokenValidator).generateRefreshToken("user-1", "user@example.com", 3);
    }

    @Test
    void googleLoginWithCode_exchangesCodeVerifiesIdTokenThenIssuesTokens() {
        String redirectUri = "https://slapstat.com/auth/google/callback";
        when(googleCodeExchanger.exchange("auth-code", redirectUri)).thenReturn("google-id-token");
        when(googleTokenVerifier.verify("google-id-token"))
                .thenReturn(new GoogleIdentity("google-sub-9", "g@example.com"));
        when(databaseServiceClient.findOrCreateGoogleUser("g@example.com", "google-sub-9"))
                .thenReturn(new User("user-9", "g@example.com", null, 0, true));
        when(jwtTokenValidator.generateToken(anyString(), anyString(), anyBoolean())).thenReturn("access");
        when(jwtTokenValidator.generateRefreshToken(anyString(), anyString(), anyInt())).thenReturn("refresh");

        AuthResponse response =
                authService.googleLoginWithCode(new GoogleCodeLoginRequest("auth-code", redirectUri));

        assertThat(response.token()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(databaseServiceClient).findOrCreateGoogleUser("g@example.com", "google-sub-9");
    }
}
