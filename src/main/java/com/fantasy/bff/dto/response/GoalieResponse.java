package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record GoalieResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        String teamAbbrev,
        @Schema(description = "The player's headshot: an absolute URL when the picture lives on "
                + "the platform's image CDN, or a path relative to the API base URL "
                + "(e.g. /players/9245/headshot?v=96-1.55-0.04) when this service serves it. The "
                + "query string is opaque and marks how the avatar was drawn, so that reframing it "
                + "does not leave a cached picture behind: use the value as given. Absent when the "
                + "player has no picture.")
        String headshot,
        @Schema(description = "Jersey number; absent for players the platform has not assigned one")
                Integer sweaterNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Stats stats
) {
    /**
     * The same goalie on a different club. The pool's own label is a sync snapshot, so
     * {@code PlayerService} replaces it with the club the NHL lists him on today.
     */
    public GoalieResponse withTeamAbbrev(String team) {
        return new GoalieResponse(id, name, team, headshot, sweaterNumber, stats);
    }

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
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Overtime losses; sourced from ESPN") int otl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sho,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sa,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sv,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ga,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double gaa,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double svPct,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Share of decisions won, as a fraction") double winPct,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Total time on ice for the season, in seconds; sourced from ESPN") int toi
    ) {}
}
