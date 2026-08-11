package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetUsernameRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Letters, digits and underscores, 3–20 characters. Shown publicly "
                        + "wherever the account publishes something.")
        @NotBlank
        @Size(min = 3, max = 20)
        @Pattern(regexp = "^[A-Za-z0-9_]+$",
                message = "may contain only letters, digits and underscores")
        String username
) {}
