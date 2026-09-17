package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Every NHL team's schedule over a stretch of dates, best for skaters first")
public record ScheduleStrengthResponse(
        @Schema(description = "The year the season starts in. Absent when no schedule is published") Integer season,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate start,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate end,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "A night with this many games or fewer is an off-night")
                int offNightMaxGames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Only the nights with games") List<ScheduleNight> nights,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TeamSchedule> teams) {}
