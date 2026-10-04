package com.fantasy.bff.controller;

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
import com.fantasy.bff.dto.response.AuthResponse;
import com.fantasy.bff.security.RefreshTokenCookie;
import com.fantasy.bff.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register and login endpoints")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    public AuthController(AuthService authService, RefreshTokenCookie refreshTokenCookie) {
        this.authService = authService;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with email and password to receive a JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return issued(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens",
            description = "Exchange a valid refresh token for a new access token and refresh token pair. "
                    + "The token is read from the HttpOnly slapstat_refresh cookie, else from the body "
                    + "(legacy clients). With neither, the answer is 401: there is no session to refresh.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "No refresh token, or an invalid, expired or revoked one"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<AuthResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookie.NAME, required = false) String cookieToken,
            @Valid @RequestBody(required = false) RefreshRequest request,
            HttpServletResponse response) {
        boolean cookiePresented = StringUtils.hasText(cookieToken);
        try {
            String refreshToken = presentedRefreshToken(cookieToken, request);
            return issued(HttpStatus.OK, authService.refresh(new RefreshRequest(refreshToken)));
        } catch (SecurityException refused) {
            if (cookiePresented) {
                response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString());
            }
            throw refused;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign this browser out",
            description = "Clears the refresh-token cookie. Nothing is revoked server-side, so the account's "
                    + "sessions on other browsers and devices carry on; signing out everywhere is "
                    + "POST /api/v1/account/sessions/revoke.")
    @ApiResponse(responseCode = "204", description = "Cookie cleared")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString())
                .build();
    }

    @PostMapping("/google")
    @Operation(summary = "Login with Google", description = "Verify a Google ID token and log the user in, registering a new account on first login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid Google ID token or unverified email"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return issued(HttpStatus.OK, authService.googleLogin(request));
    }

    @PostMapping("/google/code")
    @Operation(summary = "Login with a Google authorization code",
            description = "Exchange a Google OAuth authorization code (from the top-level redirect flow) "
                    + "for an ID token server-side and log the user in, registering a new account on first login. "
                    + "Used by browsers that block the embedded Google Sign-In button, such as iOS Safari.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired authorization code, or unverified email"),
            @ApiResponse(responseCode = "400", description = "Unrecognized redirect URI or validation error")
    })
    public ResponseEntity<AuthResponse> googleCodeLogin(@Valid @RequestBody GoogleCodeLoginRequest request) {
        return issued(HttpStatus.OK, authService.googleLoginWithCode(request));
    }

    @PostMapping("/facebook")
    @Operation(summary = "Login with Facebook", description = "Verify a Facebook access token and log the user in, registering a new account on first login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid Facebook access token or missing email"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<AuthResponse> facebookLogin(@Valid @RequestBody FacebookLoginRequest request) {
        return issued(HttpStatus.OK, authService.facebookLogin(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Register", description = "Create a new user account and return an authenticated session")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered and logged in"),
            @ApiResponse(responseCode = "400", description = "Email already in use or validation error")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return issued(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Request a password reset email",
            description = "Always returns 200. To avoid revealing whether an account exists, the response "
                    + "is identical whether or not a reset email was sent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted (an email is sent only if a matching password account exists)"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Reset password with a token",
            description = "Consumes a single-use reset token from the email link and sets a new password.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token, or validation error")
    })
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify an email address with a token",
            description = "Consumes a single-use verification token from the email link and marks "
                    + "the account's email verified.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token, or validation error")
    })
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify/resend")
    @Operation(summary = "Resend the verification email",
            description = "Always returns 200. To avoid revealing whether an account exists, the "
                    + "response is identical whether or not a verification email was sent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted (an email is sent only if a matching unverified account exists)"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request);
        return ResponseEntity.ok().build();
    }

    private String presentedRefreshToken(String cookieToken, RefreshRequest request) {
        if (StringUtils.hasText(cookieToken)) {
            if (cookieToken.length() > RefreshTokenCookie.MAX_VALUE_LENGTH) {
                throw new SecurityException("Invalid refresh token");
            }
            return cookieToken;
        }
        if (request != null && StringUtils.hasText(request.refreshToken())) {
            return request.refreshToken();
        }
        throw new SecurityException("No refresh token");
    }

    // refreshToken stays in the body while web clients loaded before the cookie still read it;
    // drop it from AuthResponse once the web no longer does.
    private ResponseEntity<AuthResponse> issued(HttpStatus status, AuthResponse tokens) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.issue(tokens.refreshToken()).toString())
                .body(tokens);
    }
}
