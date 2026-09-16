package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record YahooLinkClaimRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The one-time link code the Yahoo callback put in the web app's URL fragment.")
        @NotBlank
        @Size(max = 128)
        String code
) {}
