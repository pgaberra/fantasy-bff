package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

public record SkaterResponse(
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
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<SkaterPosition> positions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Stats stats
) {
    /**
     * The same skater on a different club. The pool's own label is a sync snapshot, so
     * {@code PlayerService} replaces it with the club the NHL lists him on today.
     */
    public SkaterResponse withTeamAbbrev(String team) {
        return new SkaterResponse(id, name, team, headshot, sweaterNumber, positions, stats);
    }

    @Schema(name = "SkaterStats")
    public record Stats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UtilityStats utility,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScoringStats scoring
    ) {}

    @Schema(name = "SkaterUtilityStats")
    public record UtilityStats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int gp,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int toiPerGame
    ) {}

    @Schema(name = "SkaterScoringStats")
    public record ScoringStats(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int goals,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int assists,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int points,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int plusMinus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pim,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ppg,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ppa,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ppp,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int shg,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sha,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int shp,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Special-teams goals (power play + shorthanded)") int stpg,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Special-teams assists") int stpa,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Special-teams points") int stp,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int gwg,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Games with three or more goals; sourced from ESPN") int hatTricks,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sog,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double shPct,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int fw,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int fl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int hits,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int blocks,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Points scored while eligible at defence; zero for forwards") int defPoints,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Shifts taken over the season; sourced from ESPN") int shifts,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Total time on ice for the season, in seconds") int toi
    ) {}
}
