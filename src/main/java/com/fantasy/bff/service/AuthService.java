package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.ForgotPasswordRequest;
import com.fantasy.bff.dto.request.GoogleLoginRequest;
import com.fantasy.bff.dto.request.LoginRequest;
import com.fantasy.bff.dto.request.RefreshRequest;
import com.fantasy.bff.dto.request.RegisterRequest;
import com.fantasy.bff.dto.request.ResetPasswordRequest;
import io.jsonwebtoken.Claims;
import com.fantasy.bff.dto.response.AuthResponse;
import com.fantasy.bff.email.PasswordResetEmailSender;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.security.GoogleIdentity;
import com.fantasy.bff.security.GoogleTokenVerifier;
import com.fantasy.bff.security.JwtTokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final DatabaseServiceClient databaseServiceClient;
    private final JwtTokenValidator jwtTokenValidator;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final PasswordResetEmailSender passwordResetEmailSender;
    private final String webBaseUrl;

    public AuthService(DatabaseServiceClient databaseServiceClient,
                       JwtTokenValidator jwtTokenValidator,
                       PasswordEncoder passwordEncoder,
                       GoogleTokenVerifier googleTokenVerifier,
                       PasswordResetEmailSender passwordResetEmailSender,
                       @Value("${app.web-base-url:http://localhost:4200}") String webBaseUrl) {
        this.databaseServiceClient = databaseServiceClient;
        this.jwtTokenValidator = jwtTokenValidator;
        this.passwordEncoder = passwordEncoder;
        this.googleTokenVerifier = googleTokenVerifier;
        this.passwordResetEmailSender = passwordResetEmailSender;
        this.webBaseUrl = webBaseUrl;
    }

    public AuthResponse login(LoginRequest request) {
        User user = databaseServiceClient.findUserByEmail(request.email())
                .orElseThrow(() -> new SecurityException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new SecurityException("Invalid email or password");
        }

        return issueTokens(user.id(), user.email());
    }

    public AuthResponse refresh(RefreshRequest request) {
        Claims claims = jwtTokenValidator.validateAndExtractRefreshTokenClaims(request.refreshToken());
        return issueTokens(claims.getSubject(), claims.get("email", String.class));
    }

    /**
     * Creates the account and logs the user straight in — registration returns the same
     * token pair as login, so the client never has to follow up with a separate login.
     */
    public AuthResponse register(RegisterRequest request) {
        if (databaseServiceClient.existsByEmail(request.email())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = databaseServiceClient.createUser(request.email(), passwordHash);
        return issueTokens(user.id(), user.email());
    }

    /**
     * Logs in (or registers) via a Google ID token. The verifier guarantees signature,
     * audience and a verified email; db-service resolves the account by Google subject,
     * links it to an existing same-email account, or creates a new password-less user.
     */
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        GoogleIdentity identity = googleTokenVerifier.verify(request.idToken());
        User user = databaseServiceClient.findOrCreateGoogleUser(identity.email(), identity.sub());
        return issueTokens(user.id(), user.email());
    }

    /**
     * Starts the forgot-password flow. Emails a single-use reset link when the address
     * maps to a resettable password account; otherwise does nothing. The caller responds
     * identically in both cases so a request never reveals whether an account exists.
     */
    public void requestPasswordReset(ForgotPasswordRequest request) {
        databaseServiceClient.createPasswordResetToken(request.email()).ifPresentOrElse(
                token -> passwordResetEmailSender.send(
                        request.email(), buildResetLink(token.token()), token.expiresAt()),
                () -> log.info("Password reset requested for a non-resettable account; no email sent"));
    }

    public void resetPassword(ResetPasswordRequest request) {
        String passwordHash = passwordEncoder.encode(request.newPassword());
        databaseServiceClient.resetPassword(request.token(), passwordHash);
    }

    private String buildResetLink(String token) {
        return UriComponentsBuilder.fromUriString(webBaseUrl)
                .path("/reset-password")
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private AuthResponse issueTokens(String userId, String email) {
        String token = jwtTokenValidator.generateToken(userId, email);
        String refreshToken = jwtTokenValidator.generateRefreshToken(userId, email);
        long expiresIn = jwtTokenValidator.getExpirationMs() / 1000;
        long refreshExpiresIn = jwtTokenValidator.getRefreshExpirationMs() / 1000;
        return new AuthResponse(token, expiresIn, refreshToken, refreshExpiresIn);
    }
}
