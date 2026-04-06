package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

public record SkaterResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlayerType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<String> positions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Stats stats
) {
    public record Stats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UtilityStats utility,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScoringStats scoring
    ) {}

    public record UtilityStats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int gp,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int toiPerGame
    ) {}

    public record ScoringStats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int goals,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int assists,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int plusMinus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pim,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ppg,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ppa,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int shg,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sha,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int gwg,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sog,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double shPct,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int fw,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int fl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int hits,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int blocks
    ) {}
}
