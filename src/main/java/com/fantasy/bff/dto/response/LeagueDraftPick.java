package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record LeagueDraftPick(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Overall pick number, from 1.")
        int overall,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int round,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The id of the team that made the pick.")
        String teamId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The player, by the pool's own id.")
        int playerId
) {}
