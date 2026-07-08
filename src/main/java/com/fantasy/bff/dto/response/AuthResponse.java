package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresInSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long refreshExpiresInSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean admin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the account's email address has been verified") boolean emailVerified
) {}
