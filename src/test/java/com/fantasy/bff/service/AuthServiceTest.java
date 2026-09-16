package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.config.SecurityProperties;
import com.fantasy.bff.dto.request.GoogleCodeLoginRequest;
import com.fantasy.bff.dto.request.LoginRequest;
import com.fantasy.bff.dto.request.RefreshRequest;
import com.fantasy.bff.dto.request.RegisterRequest;
import com.fantasy.bff.dto.request.ResendVerificationRequest;
import com.fantasy.bff.dto.request.ForgotPasswordRequest;
import com.fantasy.bff.dto.response.AuthResponse;
import com.fantasy.bff.email.EmailVerificationEmailSender;
import com.fantasy.bff.email.PasswordResetEmailSender;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.security.FacebookTokenVerifier;
import com.fantasy.bff.security.GoogleCodeExchanger;
import com.fantasy.bff.security.GoogleIdentity;
import com.fantasy.bff.security.GoogleTokenVerifier;
import com.fantasy.bff.security.EmailSendThrottle;
import com.fantasy.bff.security.JwtTokenValidator;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private DatabaseServiceClient databaseServiceClient;
    private JwtTokenValidator jwtTokenValidator;
    private PasswordEncoder passwordEncoder;
    private GoogleTokenVerifier googleTokenVerifier;
    private GoogleCodeExchanger googleCodeExchanger;
    private EmailSendThrottle emailSendThrottle;
    private PasswordResetEmailSender passwordResetEmailSender;
    private EmailVerificationEmailSender emailVerificationEmailSender;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        databaseServiceClient = mock(DatabaseServiceClient.class);
        jwtTokenValidator = mock(JwtTokenValidator.class);
        passwordEncoder = mock(PasswordEncoder.class);
        googleTokenVerifier = mock(GoogleTokenVerifier.class);
        googleCodeExchanger = mock(GoogleCodeExchanger.class);
        emailSendThrottle = mock(EmailSendThrottle.class);
        when(emailSendThrottle.tryAcquire(anyString(), anyString())).thenReturn(true);
        passwordResetEmailSender = mock(PasswordResetEmailSender.class);
        emailVerificationEmailSender = mock(EmailVerificationEmailSender.class);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$dummyDummyDummyDummyDummyDummyDummyDummyDummyDummyDu");
        authService = authServiceWithAdmins();
    }

    private AuthService authServiceWithAdmins(String... adminEmails) {
        return new AuthService(
                databaseServiceClient,
                jwtTokenValidator,
                passwordEncoder,
                googleTokenVerifier,
                googleCodeExchanger,
                mock(FacebookTokenVerifier.class),
                passwordResetEmailSender,
                emailVerificationEmailSender,
                new SecurityProperties(List.of(), List.of(), List.of(adminEmails), false),
                emailSendThrottle,
                "http://localhost:4200");
    }

    @Test
    void register_withAllowlistedButUnverifiedEmail_issuesNoAdminClaim() {
        authService = authServiceWithAdmins("boss@example.com");
        when(databaseServiceClient.createUser(eq("boss@example.com"), anyString()))
                .thenReturn(new User("user-2", "boss@example.com", null, "hash", 0, false));

        AuthResponse response = authService.register(new RegisterRequest("boss@example.com", "Passw0rd!"));

        // Registering an allowlisted address proves nothing about owning it: whoever got there
        // first would be an admin. The claim waits until the verification link has been followed.
        assertThat(response.admin()).isFalse();
        verify(jwtTokenValidator).generateToken("user-2", "boss@example.com", false);
    }

    @Test
    void login_withAllowlistedVerifiedEmail_issuesAdminClaim() {
        authService = authServiceWithAdmins("Boss@Example.com");
        when(databaseServiceClient.findUserByEmail("boss@example.com"))
                .thenReturn(Optional.of(new User("user-2", "boss@example.com", null, "hash", 0, true)));
        when(passwordEncoder.matches("Passw0rd!", "hash")).thenReturn(true);

        AuthResponse response = authService.login(new LoginRequest("boss@example.com", "Passw0rd!"));

        assertThat(response.admin()).isTrue();
        verify(jwtTokenValidator).generateToken("user-2", "boss@example.com", true);
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
        User socialUser = new User("user-1", "social@example.com", null, null, 0, true);
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
                .thenReturn(Optional.of(new User("user-1", "user@example.com", null, "hash", 1, true)));

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
                .thenReturn(Optional.of(new User("user-1", "user@example.com", null, "hash", 3, true)));
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
                .thenReturn(new User("user-9", "g@example.com", null, null, 0, true));
        when(jwtTokenValidator.generateToken(anyString(), anyString(), anyBoolean())).thenReturn("access");
        when(jwtTokenValidator.generateRefreshToken(anyString(), anyString(), anyInt())).thenReturn("refresh");

        AuthResponse response =
                authService.googleLoginWithCode(new GoogleCodeLoginRequest("auth-code", redirectUri));

        assertThat(response.token()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(databaseServiceClient).findOrCreateGoogleUser("g@example.com", "google-sub-9");
    }

    @Test
    void resendVerification_overTheAddressCap_sendsNothingAndAsksNothingOfTheDatabase() {
        when(emailSendThrottle.tryAcquire("verification", "victim@example.com")).thenReturn(false);

        authService.resendVerificationEmail(new ResendVerificationRequest("victim@example.com"));

        verifyNoInteractions(databaseServiceClient, emailVerificationEmailSender);
    }

    @Test
    void forgotPassword_overTheAddressCap_sendsNothingAndAsksNothingOfTheDatabase() {
        when(emailSendThrottle.tryAcquire("password-reset", "victim@example.com")).thenReturn(false);

        authService.requestPasswordReset(new ForgotPasswordRequest("victim@example.com"));

        verifyNoInteractions(databaseServiceClient, passwordResetEmailSender);
    }

    @Test
    void signOutEverywhere_revokesEverySessionOfThatAccount() {
        authService.signOutEverywhere("11111111-1111-1111-1111-111111111111");

        verify(databaseServiceClient).revokeSessions(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }
}
