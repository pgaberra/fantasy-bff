package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** One of the model's lines for a player: the games it covers and the totals over them. */
@Schema(description = "A projected line: the games it covers and the player's totals over them")
public record ScoutLine(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Games the line covers: games played for a skater, starts for a goalie")
                double games,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The projected totals over those games, in the projection's own stat "
                        + "vocabulary. Rates (toiPerGame, shPct, svPct, gaa) are per game or per shot "
                        + "and do not scale with the games.")
                Map<String, Double> stats) {}
