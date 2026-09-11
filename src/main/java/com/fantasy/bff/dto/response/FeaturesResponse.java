package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record FeaturesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether this environment serves the AI projection: the model's lines "
                        + "and a projection or preset draft seeded from them. False means neither is "
                        + "served to anyone, so a client should not offer it. It says nothing about "
                        + "this account's plan: an AI projection that needs premium is still available.")
        boolean aiProjection
) {}
