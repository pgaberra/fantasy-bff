package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * What a player actually produced over a stretch of their team's schedule.
 *
 * <p>These are measured totals, not a forecast — the distinction matters, because a hot twenty
 * games reads like a prediction if nothing says otherwise.
 */
@Schema(description = "A player's measured totals over a range of their team's games")
public record PlayerSplitResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The platform's player id")
                int playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        String teamAbbrev,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "skater or goalie")
                String type,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Games the player appeared in within the range")
                int games,
        @Schema(description = "First team game number covered")
                Integer firstTeamGame,
        @Schema(description = "Last team game number covered") Integer lastTeamGame,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Totals over the range")
                Map<String, Double> stats) {}
