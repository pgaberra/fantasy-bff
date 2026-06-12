package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Google ID token (JWT) issued by Google Identity Services")
        @NotBlank
        @Size(max = 4096)
        String idToken
) {}
