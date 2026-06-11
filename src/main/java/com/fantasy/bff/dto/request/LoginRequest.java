package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @NotBlank @Size(max = 72) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String password
) {}
