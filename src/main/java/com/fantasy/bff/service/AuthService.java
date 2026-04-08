package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.LoginRequest;
import com.fantasy.bff.dto.request.RefreshRequest;
import com.fantasy.bff.dto.request.RegisterRequest;
import io.jsonwebtoken.Claims;
import com.fantasy.bff.dto.response.AuthResponse;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.security.JwtTokenValidator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final DatabaseServiceClient databaseServiceClient;
    private final JwtTokenValidator jwtTokenValidator;
    private final PasswordEncoder passwordEncoder;

    public AuthService(DatabaseServiceClient databaseServiceClient,
                       JwtTokenValidator jwtTokenValidator,
                       PasswordEncoder passwordEncoder) {
        this.databaseServiceClient = databaseServiceClient;
        this.jwtTokenValidator = jwtTokenValidator;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse login(LoginRequest request) {
        User user = databaseServiceClient.findUserByEmail(request.email())
                .orElseThrow(() -> new SecurityException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new SecurityException("Invalid email or password");
        }

        String token = jwtTokenValidator.generateToken(user.id(), user.email());
        String refreshToken = jwtTokenValidator.generateRefreshToken(user.id(), user.email());
        long expiresIn = jwtTokenValidator.getExpirationMs() / 1000;
        long refreshExpiresIn = jwtTokenValidator.getRefreshExpirationMs() / 1000;
        return new AuthResponse(token, expiresIn, refreshToken, refreshExpiresIn);
    }

    public AuthResponse refresh(RefreshRequest request) {
        Claims claims = jwtTokenValidator.validateAndExtractClaims(request.refreshToken());
        if (!jwtTokenValidator.isRefreshToken(claims)) {
            throw new SecurityException("Invalid refresh token");
        }
        String userId = claims.getSubject();
        String email = claims.get("email", String.class);
        String newToken = jwtTokenValidator.generateToken(userId, email);
        String newRefreshToken = jwtTokenValidator.generateRefreshToken(userId, email);
        long expiresIn = jwtTokenValidator.getExpirationMs() / 1000;
        long refreshExpiresIn = jwtTokenValidator.getRefreshExpirationMs() / 1000;
        return new AuthResponse(newToken, expiresIn, newRefreshToken, refreshExpiresIn);
    }

    public void register(RegisterRequest request) {
        if (databaseServiceClient.existsByEmail(request.email())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        databaseServiceClient.createUser(request.email(), passwordHash);
    }
}
