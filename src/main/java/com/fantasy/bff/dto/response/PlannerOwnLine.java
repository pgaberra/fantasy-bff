package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * The model's line for one of the user's own players over the chosen stretch, on the same scale as
 * a free agent's ({@link FreeAgentResponse}), so the client can weigh a pickup against the player
 * it would replace, game for game.
 */
@Schema(description = "The model's line for one of the user's players over the chosen stretch")
public record PlannerOwnLine(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The platform's player id, as the team's players list gives it")
                String playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Games his club plays in the stretch")
                int clubGames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Games he is expected to dress for, or starts for a goalie")
                double expectedGames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The model's projected totals over those games, in the vocabulary a "
                        + "free agent's line uses. Rates (toiPerGame, shPct, svPct, gaa) are the "
                        + "season's own and do not scale with the stretch.")
                Map<String, Double> stats) {}
