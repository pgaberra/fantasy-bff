package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @NotBlank @Size(min = 8) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String password
) {}
