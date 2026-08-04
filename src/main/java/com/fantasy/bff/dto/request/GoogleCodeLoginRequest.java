package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for the Google OAuth 2.0 authorization-code flow. The web performs a top-level
 * redirect to Google (which works on browsers that block the embedded Sign-In button, e.g.
 * iOS Safari under Intelligent Tracking Prevention) and posts the returned {@code code} plus
 * the {@code redirectUri} it used; the BFF exchanges them server-side for an ID token.
 */
public record GoogleCodeLoginRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Authorization code returned by Google's OAuth redirect")
        @NotBlank
        @Size(max = 2048)
        String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Redirect URI used in the authorization request; must match one the BFF allows")
        @NotBlank
        @Size(max = 512)
        String redirectUri
) {}
