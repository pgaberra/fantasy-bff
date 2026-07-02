package com.fantasy.bff.controller;

import com.fantasy.bff.dto.request.FacebookLoginRequest;
import com.fantasy.bff.dto.request.ForgotPasswordRequest;
import com.fantasy.bff.dto.request.GoogleLoginRequest;
import com.fantasy.bff.dto.request.LoginRequest;
import com.fantasy.bff.dto.request.RefreshRequest;
import com.fantasy.bff.dto.request.RegisterRequest;
import com.fantasy.bff.dto.request.ResetPasswordRequest;
import com.fantasy.bff.dto.response.AuthResponse;
import com.fantasy.bff.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register and login endpoints")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with email and password to receive a JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens", description = "Exchange a valid refresh token for a new access token and refresh token pair")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/google")
    @Operation(summary = "Login with Google", description = "Verify a Google ID token and log the user in, registering a new account on first login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid Google ID token or unverified email"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.googleLogin(request));
    }

    @PostMapping("/facebook")
    @Operation(summary = "Login with Facebook", description = "Verify a Facebook access token and log the user in, registering a new account on first login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid Facebook access token or missing email"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<AuthResponse> facebookLogin(@Valid @RequestBody FacebookLoginRequest request) {
        return ResponseEntity.ok(authService.facebookLogin(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Register", description = "Create a new user account and return an authenticated session")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered and logged in"),
            @ApiResponse(responseCode = "400", description = "Email already in use or validation error")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
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
}
