package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The espn_s2 + SWID cookies the user copies from their browser to read their private league. */
public record EspnCredentialsRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The user's espn_s2 cookie value.")
        @NotBlank @Size(max = 1024) String espnS2,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The user's SWID cookie value (including the braces).")
        @NotBlank @Size(max = 128) String swid
) {
}
