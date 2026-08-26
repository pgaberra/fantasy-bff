package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * A player's numbers, split by whether they score in the league or are only there to be read.
 *
 * <p>Open maps rather than named fields: which stats a league counts is the league's business,
 * and the set changes with the platform's settings. Neither side interprets the keys — the
 * client names them, we store and return them.
 */
public record PlayerStats(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, Double> utility,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, Double> scoring
) {

    public static PlayerStats from(com.fantasy.bff.generated.db.model.PlayerStats stats) {
        return stats == null ? null : new PlayerStats(stats.getUtility(), stats.getScoring());
    }

    public com.fantasy.bff.generated.db.model.PlayerStats toDownstream() {
        return new com.fantasy.bff.generated.db.model.PlayerStats()
                .utility(utility)
                .scoring(scoring);
    }
}
