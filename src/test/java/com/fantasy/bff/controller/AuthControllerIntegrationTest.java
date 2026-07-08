package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.FacebookLoginRequest;
import com.fantasy.bff.dto.request.ForgotPasswordRequest;
import com.fantasy.bff.dto.request.GoogleLoginRequest;
import com.fantasy.bff.dto.request.LoginRequest;
import com.fantasy.bff.dto.request.RefreshRequest;
import com.fantasy.bff.dto.request.RegisterRequest;
import com.fantasy.bff.dto.request.ResendVerificationRequest;
import com.fantasy.bff.dto.request.ResetPasswordRequest;
import com.fantasy.bff.dto.request.VerifyEmailRequest;
import com.fantasy.bff.email.EmailVerificationEmailSender;
import com.fantasy.bff.email.PasswordResetEmailSender;
import com.fantasy.bff.model.downstream.EmailVerificationToken;
import com.fantasy.bff.model.downstream.PasswordResetToken;
import com.fantasy.bff.security.FacebookIdentity;
import com.fantasy.bff.security.FacebookTokenVerifier;
import com.fantasy.bff.security.GoogleIdentity;
import com.fantasy.bff.security.GoogleTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fantasy.bff.model.downstream.User;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private DatabaseServiceClient databaseServiceClient;

    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    @MockitoBean
    private FacebookTokenVerifier facebookTokenVerifier;

    @MockitoBean
    private PasswordResetEmailSender passwordResetEmailSender;

    @MockitoBean
    private EmailVerificationEmailSender emailVerificationEmailSender;

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password");
        when(databaseServiceClient.findUserByEmail("test@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void register_withExistingEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("test@example.com", "password");
        when(databaseServiceClient.existsByEmail("test@example.com")).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void register_withOversizedPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("test@example.com", "a".repeat(73));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void register_withNewEmail_returns201WithTokens() throws Exception {
        String email = "new@example.com";
        RegisterRequest request = new RegisterRequest(email, "password");
        when(databaseServiceClient.existsByEmail(email)).thenReturn(false);
        when(databaseServiceClient.createUser(eq(email), anyString()))
                .thenReturn(new User("user-7", email, "stored-hash", 0));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.refreshToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshExpiresInSeconds").isNumber())
                .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void login_withValidCredentials_returnsBothTokens() throws Exception {
        String email = "user@example.com";
        String rawPassword = "secret";
        User user = new User("user-1", email, passwordEncoder.encode(rawPassword), 0);
        when(databaseServiceClient.findUserByEmail(email)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, rawPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.refreshToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshExpiresInSeconds").isNumber());
    }

    @Test
    void googleLogin_withValidToken_returnsBothTokens() throws Exception {
        when(googleTokenVerifier.verify("valid-google-token"))
                .thenReturn(new GoogleIdentity("google-sub-1", "g@example.com"));
        when(databaseServiceClient.findOrCreateGoogleUser("g@example.com", "google-sub-1"))
                .thenReturn(new User("user-3", "g@example.com", null, 0));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("valid-google-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.refreshToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshExpiresInSeconds").isNumber());
    }

    @Test
    void googleLogin_withInvalidToken_returns401() throws Exception {
        when(googleTokenVerifier.verify("bad-token"))
                .thenThrow(new SecurityException("Invalid Google ID token"));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("bad-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void googleLogin_withBlankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void facebookLogin_withValidToken_returnsBothTokens() throws Exception {
        when(facebookTokenVerifier.verify("valid-fb-token"))
                .thenReturn(new FacebookIdentity("fb-sub-1", "f@example.com"));
        when(databaseServiceClient.findOrCreateFacebookUser("f@example.com", "fb-sub-1"))
                .thenReturn(new User("user-4", "f@example.com", null, 0));

        mockMvc.perform(post("/api/v1/auth/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FacebookLoginRequest("valid-fb-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.refreshToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshExpiresInSeconds").isNumber());
    }

    @Test
    void facebookLogin_withInvalidToken_returns401() throws Exception {
        when(facebookTokenVerifier.verify("bad-token"))
                .thenThrow(new SecurityException("Invalid Facebook access token"));

        mockMvc.perform(post("/api/v1/auth/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FacebookLoginRequest("bad-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void facebookLogin_withBlankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FacebookLoginRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void refresh_withValidRefreshToken_returnsNewTokenPair() throws Exception {
        String email = "user@example.com";
        String rawPassword = "secret";
        User user = new User("user-1", email, passwordEncoder.encode(rawPassword), 0);
        when(databaseServiceClient.findUserByEmail(email)).thenReturn(Optional.of(user));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, rawPassword))))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyOrNullString())));
    }

    @Test
    void refresh_withAccessTokenInsteadOfRefreshToken_returns401() throws Exception {
        String email = "user@example.com";
        String rawPassword = "secret";
        User user = new User("user-1", email, passwordEncoder.encode(rawPassword), 0);
        when(databaseServiceClient.findUserByEmail(email)).thenReturn(Optional.of(user));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, rawPassword))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(accessToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_sendsEmail_whenAccountResettable() throws Exception {
        when(databaseServiceClient.createPasswordResetToken("user@example.com"))
                .thenReturn(Optional.of(new PasswordResetToken("raw-token", Instant.now().plusSeconds(1800))));

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("user@example.com"))))
                .andExpect(status().isOk());

        verify(passwordResetEmailSender)
                .send(eq("user@example.com"), contains("/reset-password?token=raw-token"), any());
    }

    @Test
    void forgotPassword_returns200_andSendsNothing_whenNoResettableAccount() throws Exception {
        when(databaseServiceClient.createPasswordResetToken("ghost@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("ghost@example.com"))))
                .andExpect(status().isOk());

        verifyNoInteractions(passwordResetEmailSender);
    }

    @Test
    void forgotPassword_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void resetPassword_withValidToken_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("raw-token", "newsecret"))))
                .andExpect(status().isOk());

        verify(databaseServiceClient).resetPassword(eq("raw-token"), anyString());
    }

    @Test
    void resetPassword_withInvalidToken_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Invalid or expired password reset token"))
                .when(databaseServiceClient).resetPassword(eq("bad-token"), anyString());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("bad-token", "newsecret"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void resetPassword_withShortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"raw-token\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void register_sendsVerificationEmail() throws Exception {
        String email = "verify@example.com";
        when(databaseServiceClient.existsByEmail(email)).thenReturn(false);
        when(databaseServiceClient.createUser(eq(email), anyString()))
                .thenReturn(new User("user-9", email, "stored-hash", 0));
        when(databaseServiceClient.createEmailVerificationToken(email))
                .thenReturn(Optional.of(new EmailVerificationToken("verify-token", Instant.now().plusSeconds(86400))));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password"))))
                .andExpect(status().isCreated());

        verify(emailVerificationEmailSender)
                .send(eq(email), contains("/verify-email?token=verify-token"), any());
    }

    @Test
    void verifyEmail_withValidToken_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest("good-token"))))
                .andExpect(status().isOk());

        verify(databaseServiceClient).verifyEmail("good-token");
    }

    @Test
    void verifyEmail_withInvalidToken_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Invalid or expired email verification token"))
                .when(databaseServiceClient).verifyEmail("bad-token");

        mockMvc.perform(post("/api/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest("bad-token"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void resendVerification_sendsEmail_whenAccountUnverified() throws Exception {
        when(databaseServiceClient.createEmailVerificationToken("user@example.com"))
                .thenReturn(Optional.of(new EmailVerificationToken("raw-token", Instant.now().plusSeconds(86400))));

        mockMvc.perform(post("/api/v1/auth/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendVerificationRequest("user@example.com"))))
                .andExpect(status().isOk());

        verify(emailVerificationEmailSender)
                .send(eq("user@example.com"), contains("/verify-email?token=raw-token"), any());
    }

    @Test
    void resendVerification_returns200_andSendsNothing_whenNothingToVerify() throws Exception {
        when(databaseServiceClient.createEmailVerificationToken("ghost@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendVerificationRequest("ghost@example.com"))))
                .andExpect(status().isOk());

        verifyNoInteractions(emailVerificationEmailSender);
    }
}
