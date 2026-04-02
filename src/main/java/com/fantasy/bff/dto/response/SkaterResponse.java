package com.fantasy.bff.dto.response;

import java.util.Set;

public record SkaterResponse(
        String type,
        int id,
        String name,
        Set<String> positions,
        Stats stats
) {
    public record Stats(
            UtilityStats utility,
            ScoringStats scoring
    ) {}

    public record UtilityStats(
            int gp,
            int toiPerGame
    ) {}

    public record ScoringStats(
            int goals,
            int assists,
            int plusMinus,
            int pim,
            int ppg,
            int ppa,
            int shg,
            int sha,
            int gwg,
            int sog,
            double shPct,
            int fw,
            int fl,
            int hits,
            int blocks
    ) {}
}
