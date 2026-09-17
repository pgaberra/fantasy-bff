package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record LeagueDraftResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LeagueDraftStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for an auction draft, which the draft room does not follow.")
        boolean auction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The league's teams in first-round draft order once the league has set it, "
                        + "otherwise in the platform's own team order.")
        List<LeagueDraftTeam> teams,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The picks made so far, in order and without gaps: pick n is at index n - 1.")
        List<LeagueDraftPick> picks
) {}
