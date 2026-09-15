package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A season a split can be measured over")
public record SplitSeason(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The year the season starts in")
                int season,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Games in the season's schedule: 82, or 84 from 2026-27")
                int scheduleGames,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "The furthest any team has got; 0 before the season's first game")
                int gamesPlayed) {}
