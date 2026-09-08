package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminGrantPremiumRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Email address of the account to give premium to")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How many months the grant lasts, counted from now")
        @Min(1) @Max(24) int months,

        @Schema(description = "Free-text note on why premium was given")
        @Size(max = 255) String reason
) {
}
