package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

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
                double opponentGoalsFor) {}
