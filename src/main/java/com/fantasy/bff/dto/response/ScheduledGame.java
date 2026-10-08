package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;

@Schema(description = "One game on a team's schedule, with what makes it a good or bad one to stream")
public record ScheduledGame(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
        @Schema(description = "The opponent's NHL abbreviation") String opponent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean home,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "A night with few games, when most lineups have a free slot")
                boolean offNight,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The team also played the day before")
                boolean backToBack,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Goals the opponent concedes per game against the league average: 1.1 is 10% more")
                double opponentGoalsAgainst,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Goals the opponent scores per game against the league average")
                double opponentGoalsFor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What the game adds to the team's skaterScore: the night, the opponent and "
                        + "the venue together. A stretch's score is the sum over its games")
                double skaterWorth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What the game adds to the team's goalieScore")
                double goalieWorth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What the game is worth to each stat a player's line over the stretch "
                        + "moves with, against an average night: the opponent's allowance of the stat "
                        + "and the venue. Keyed by the free agents' stat keys; a stat not listed is "
                        + "worth an average night. A page counting only some nights weighs a line by "
                        + "these over the nights it keeps")
                Map<String, Double> statWorth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What the game adds to a player's plus-minus per minute of his ice, "
                        + "which moves by an amount rather than a share")
                double plusMinusPerMinute) {}
