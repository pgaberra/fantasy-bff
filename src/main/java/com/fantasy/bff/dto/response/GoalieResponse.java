package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record GoalieResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Stats stats
) {
    @Schema(name = "GoalieStats")
    public record Stats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UtilityStats utility,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScoringStats scoring
    ) {}

    @Schema(name = "GoalieUtilityStats")
    public record UtilityStats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int gp
    ) {}

    @Schema(name = "GoalieScoringStats")
    public record ScoringStats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int gs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int w,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int l,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sho,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sa,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sv,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ga,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double gaa,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double svPct
    ) {}
}
