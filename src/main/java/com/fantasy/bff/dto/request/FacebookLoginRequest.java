package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FacebookLoginRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Facebook user access token from the Facebook Login SDK")
        @NotBlank
        @Size(max = 4096)
        String accessToken
) {}
