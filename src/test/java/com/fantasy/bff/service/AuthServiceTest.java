package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.config.SecurityProperties;
import com.fantasy.bff.dto.request.LoginRequest;
import com.fantasy.bff.email.PasswordResetEmailSender;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.security.FacebookTokenVerifier;
import com.fantasy.bff.security.GoogleTokenVerifier;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private DatabaseServiceClient databaseServiceClient;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        databaseServiceClient = mock(DatabaseServiceClient.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$dummyDummyDummyDummyDummyDummyDummyDummyDummyDummyDu");
        authService = new AuthService(
                databaseServiceClient,
                mock(JwtTokenValidator.class),
                passwordEncoder,
                mock(GoogleTokenVerifier.class),
                mock(FacebookTokenVerifier.class),
                mock(PasswordResetEmailSender.class),
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
        User socialUser = new User("user-1", "social@example.com", null);
        when(databaseServiceClient.findUserByEmail("social@example.com"))
                .thenReturn(Optional.of(socialUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("social@example.com", "guess123")))
                .isInstanceOf(SecurityException.class);

        verify(passwordEncoder).matches(eq("guess123"), anyString());
    }
}
