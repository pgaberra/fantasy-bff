package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String password
) {}
