package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record LeagueDraftTeam(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The platform's own key for the team, stable for the season.",
                example = "465.l.12345.t.3")
        String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for the signed-in user's own team.")
        boolean mine
) {}
