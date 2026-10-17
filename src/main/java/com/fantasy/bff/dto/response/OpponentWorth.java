package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * What a game against one opponent at one venue is worth to a player's line, against an average
 * night: the model's per-game terms read only the opponent's allowances and the venue, so one row
 * per opponent and venue covers every game of the season.
 */
@Schema(description = "What a game against an opponent, at home or away, is worth to a player's line against an "
        + "average night, as projection-service rates it today")
public record OpponentWorth(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The opponent's NHL abbreviation")
                String opponent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The player's club plays at home, the opponent away")
                boolean home,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What the game is worth to each stat against an average night: 1.1 is 10% more. "
                        + "Keyed by the players' stat keys; a stat not listed is worth an average night")
                Map<String, Double> statWorth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What the game adds to a player's plus-minus per minute of his ice, which "
                        + "moves by an amount rather than a share")
                double plusMinusPerMinute) {}
