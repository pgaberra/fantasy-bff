package com.fantasy.bff.dto.request;

import com.fantasy.bff.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @NotBlank @Size(min = 8, max = 72) @StrongPassword @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String password
) {}
