package com.fantasy.bff.dto.response;

public record GoalieResponse(
        String type,
        int id,
        String name,
        Stats stats
) {
    public record Stats(
            UtilityStats utility,
            ScoringStats scoring
    ) {}

    public record UtilityStats(
            int gp
    ) {}

    public record ScoringStats(
            int gs,
            int w,
            int l,
            int sho,
            int sa,
            int sv,
            int ga,
            double gaa,
            double svPct
    ) {}
}
