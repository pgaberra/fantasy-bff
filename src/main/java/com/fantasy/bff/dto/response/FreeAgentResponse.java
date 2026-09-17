package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * A player the league has available, with what the model expects of him over the chosen week.
 *
 * <p>The stats are the same vocabulary a projection's rows use, so a client scores them with the
 * league's own scoring settings rather than being handed a ranking it cannot re-weight.
 */
@Schema(description = "An available player and the model's line for him over the chosen stretch")
public record FreeAgentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The platform's player id")
                String playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "The club he is on, in the platform's own spelling") String teamAbbrev,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "skater or goalie")
                String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every position the league may start him at")
                List<String> positions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "FREE_AGENT to add now, WAIVERS to claim first, UNKNOWN when the "
                        + "platform does not say")
                PlayerAvailability availability,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Games his club plays in the stretch")
                int clubGames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Games he is expected to dress for, or starts for a goalie. Below "
                        + "clubGames for a part-time player, and nought before an injury return.")
                double expectedGames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The model's projected totals over those games, in the projection's "
                        + "own stat vocabulary. Rates (toiPerGame, shPct, svPct, gaa) are the "
                        + "season's own and do not scale with the stretch.")
                Map<String, Double> stats) {}
