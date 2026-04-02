package com.fantasy.bff.dto.response;

public record AuthResponse(
        String token,
        long expiresIn
) {}
