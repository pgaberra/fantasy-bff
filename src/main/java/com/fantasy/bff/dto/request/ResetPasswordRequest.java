package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 128) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @NotBlank @Size(min = 8, max = 72) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String newPassword
) {}
