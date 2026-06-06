package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresInSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long refreshExpiresInSeconds
) {}
