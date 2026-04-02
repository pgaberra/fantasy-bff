package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.LoginRequest;
import com.fantasy.bff.dto.request.RegisterRequest;
import com.fantasy.bff.dto.response.AuthResponse;
import com.fantasy.bff.exception.AuthenticationException;
import com.fantasy.bff.exception.BadRequestException;
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
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        String token = jwtTokenValidator.generateToken(user.id(), user.email());
        long expiresIn = jwtTokenValidator.getExpirationMs() / 1000;
        return new AuthResponse(token, expiresIn);
    }

    public void register(RegisterRequest request) {
        if (databaseServiceClient.existsByEmail(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        databaseServiceClient.createUser(request.username(), request.email(), passwordHash);
    }
}
