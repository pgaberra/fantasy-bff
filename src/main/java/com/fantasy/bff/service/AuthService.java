package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.FacebookLoginRequest;
import com.fantasy.bff.dto.request.ForgotPasswordRequest;
import com.fantasy.bff.dto.request.GoogleCodeLoginRequest;
import com.fantasy.bff.dto.request.GoogleLoginRequest;
import com.fantasy.bff.dto.request.LoginRequest;
import com.fantasy.bff.dto.request.RefreshRequest;
import com.fantasy.bff.dto.request.RegisterRequest;
import com.fantasy.bff.dto.request.ResendVerificationRequest;
import com.fantasy.bff.dto.request.ResetPasswordRequest;
import com.fantasy.bff.dto.request.VerifyEmailRequest;
import io.jsonwebtoken.Claims;
import com.fantasy.bff.config.SecurityProperties;
import com.fantasy.bff.dto.response.AuthResponse;
import com.fantasy.bff.email.EmailVerificationEmailSender;
import com.fantasy.bff.email.PasswordResetEmailSender;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.security.FacebookIdentity;
import com.fantasy.bff.security.FacebookTokenVerifier;
import com.fantasy.bff.security.GoogleCodeExchanger;
import com.fantasy.bff.security.GoogleIdentity;
import com.fantasy.bff.security.GoogleTokenVerifier;
import com.fantasy.bff.security.JwtTokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final DatabaseServiceClient databaseServiceClient;
    private final JwtTokenValidator jwtTokenValidator;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final GoogleCodeExchanger googleCodeExchanger;
    private final FacebookTokenVerifier facebookTokenVerifier;
    private final PasswordResetEmailSender passwordResetEmailSender;
    private final EmailVerificationEmailSender emailVerificationEmailSender;
    private final SecurityProperties securityProperties;
    private final String webBaseUrl;
    private final String dummyPasswordHash;

    public AuthService(DatabaseServiceClient databaseServiceClient,
                       JwtTokenValidator jwtTokenValidator,
                       PasswordEncoder passwordEncoder,
                       GoogleTokenVerifier googleTokenVerifier,
                       GoogleCodeExchanger googleCodeExchanger,
                       FacebookTokenVerifier facebookTokenVerifier,
                       PasswordResetEmailSender passwordResetEmailSender,
                       EmailVerificationEmailSender emailVerificationEmailSender,
                       SecurityProperties securityProperties,
                       @Value("${app.web-base-url:http://localhost:4200}") String webBaseUrl) {
        this.databaseServiceClient = databaseServiceClient;
        this.jwtTokenValidator = jwtTokenValidator;
        this.passwordEncoder = passwordEncoder;
        this.googleTokenVerifier = googleTokenVerifier;
        this.googleCodeExchanger = googleCodeExchanger;
        this.facebookTokenVerifier = facebookTokenVerifier;
        this.passwordResetEmailSender = passwordResetEmailSender;
        this.emailVerificationEmailSender = emailVerificationEmailSender;
        this.securityProperties = securityProperties;
        this.webBaseUrl = webBaseUrl;
        // A precomputed hash to compare against when an account is missing or password-less, so
        // login always runs one bcrypt regardless (see login()).
        this.dummyPasswordHash = passwordEncoder.encode("password-timing-equalizer");
    }

    public AuthResponse login(LoginRequest request) {
        Optional<User> maybeUser = databaseServiceClient.findUserByEmail(request.email());
        // Always run exactly one bcrypt comparison — against the user's hash, or a dummy hash when
        // the account is missing or password-less (social login) — so an unknown email and a wrong
        // password take the same time. Otherwise the early return for an unknown email is a timing
        // oracle that reveals which emails are registered.
        String hashToCheck = maybeUser
                .map(User::passwordHash)
                .filter(StringUtils::hasText)
                .orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (maybeUser.isEmpty() || !passwordMatches) {
            throw new SecurityException("Invalid email or password");
        }
        return issueTokens(maybeUser.get().id(), maybeUser.get().email(),
                maybeUser.get().tokenVersion(), maybeUser.get().emailVerified());
    }

    public AuthResponse refresh(RefreshRequest request) {
        Claims claims = jwtTokenValidator.validateAndExtractRefreshTokenClaims(request.refreshToken());
        User user = databaseServiceClient.findUserByEmail(claims.get("email", String.class))
                .orElseThrow(() -> new SecurityException("Invalid refresh token"));
        int currentVersion = user.tokenVersion();
        boolean tokenIsCurrent = jwtTokenValidator.getTokenVersion(claims)
                .map(version -> version == currentVersion)
                .orElse(false);
        if (!tokenIsCurrent) {
            // The refresh token was revoked — e.g. a password reset bumped the user's token version.
            throw new SecurityException("Refresh token has been revoked");
        }
        return issueTokens(user.id(), user.email(), currentVersion, user.emailVerified());
    }

    /**
     * Signs the account out on every device. Each refresh token carries the token version it was
     * issued under and {@link #refresh} refuses one that no longer matches, so bumping the version
     * ends every session at its next refresh; an access token already issued lives out its
     * 15 minutes.
     */
    public void signOutEverywhere(String userId) {
        databaseServiceClient.revokeSessions(UUID.fromString(userId));
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
        // Send a verification email, but never let its failure fail the registration: the account
        // already exists and the user can request a fresh link later (soft gate — they can still log
        // in while unverified).
        try {
            sendVerificationEmail(user.email());
        } catch (RuntimeException e) {
            log.error("Failed to issue a verification email for a newly registered account", e);
        }
        return issueTokens(user.id(), user.email(), user.tokenVersion(), user.emailVerified());
    }

    /**
     * Logs in (or registers) via a Google ID token obtained client-side (the embedded GSI
     * button). The verifier guarantees signature, audience and a verified email.
     */
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        return loginWithGoogleIdentity(googleTokenVerifier.verify(request.idToken()));
    }

    /**
     * Logs in (or registers) via the Google OAuth authorization-code flow: exchanges the code
     * for an ID token server-side, then proceeds as {@link #googleLogin}. This backs the
     * top-level-redirect Sign-In the web uses so Google login works on browsers that block the
     * embedded GSI button (notably iOS Safari under Intelligent Tracking Prevention).
     */
    public AuthResponse googleLoginWithCode(GoogleCodeLoginRequest request) {
        String idToken = googleCodeExchanger.exchange(request.code(), request.redirectUri());
        return loginWithGoogleIdentity(googleTokenVerifier.verify(idToken));
    }

    /**
     * Resolves the account for a verified Google identity and issues our token pair. db-service
     * resolves the account by Google subject, links it to an existing same-email account, or
     * creates a new password-less user.
     */
    private AuthResponse loginWithGoogleIdentity(GoogleIdentity identity) {
        User user = databaseServiceClient.findOrCreateGoogleUser(identity.email(), identity.sub());
        return issueTokens(user.id(), user.email(), user.tokenVersion(), user.emailVerified());
    }

    /**
     * Logs in (or registers) via a Facebook access token. The verifier guarantees the token
     * is valid and was issued for our app and yields a verified email; db-service resolves
     * the account by Facebook subject, links it to an existing same-email account, or
     * creates a new password-less user.
     */
    public AuthResponse facebookLogin(FacebookLoginRequest request) {
        FacebookIdentity identity = facebookTokenVerifier.verify(request.accessToken());
        User user = databaseServiceClient.findOrCreateFacebookUser(identity.email(), identity.sub());
        return issueTokens(user.id(), user.email(), user.tokenVersion(), user.emailVerified());
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

    /**
     * Re-sends the verification email for an account, or does nothing when there is nothing to
     * verify (unknown email or an already-verified account). The caller responds identically in
     * both cases so a request never reveals whether an account exists.
     */
    public void resendVerificationEmail(ResendVerificationRequest request) {
        sendVerificationEmail(request.email());
    }

    /** Consumes a verification link's token and marks the account's email verified. */
    public void verifyEmail(VerifyEmailRequest request) {
        databaseServiceClient.verifyEmail(request.token());
    }

    private void sendVerificationEmail(String email) {
        databaseServiceClient.createEmailVerificationToken(email).ifPresentOrElse(
                token -> emailVerificationEmailSender.send(
                        email, buildVerifyLink(token.token()), token.expiresAt()),
                () -> log.info("Email verification requested for an unknown or already-verified "
                        + "account; no email sent"));
    }

    private String buildResetLink(String token) {
        return UriComponentsBuilder.fromUriString(webBaseUrl)
                .path("/reset-password")
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private String buildVerifyLink(String token) {
        return UriComponentsBuilder.fromUriString(webBaseUrl)
                .path("/verify-email")
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private AuthResponse issueTokens(String userId, String email, int tokenVersion, boolean emailVerified) {
        // Admin is granted by email address, so only an address the account has proven it owns may
        // carry it. Registration signs an unverified account straight in: without this, an
        // allowlisted address that has no account yet would make whoever registers it an admin.
        boolean admin = emailVerified && isAdmin(email);
        String token = jwtTokenValidator.generateToken(userId, email, admin);
        String refreshToken = jwtTokenValidator.generateRefreshToken(userId, email, tokenVersion);
        long expiresIn = jwtTokenValidator.getExpirationMs() / 1000;
        long refreshExpiresIn = jwtTokenValidator.getRefreshExpirationMs() / 1000;
        return new AuthResponse(token, expiresIn, refreshToken, refreshExpiresIn, admin, emailVerified);
    }

    private boolean isAdmin(String email) {
        return email != null
                && securityProperties.adminEmails().contains(email.toLowerCase(Locale.ROOT));
    }
}
