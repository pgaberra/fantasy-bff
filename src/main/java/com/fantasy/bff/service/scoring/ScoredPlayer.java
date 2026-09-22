package com.fantasy.bff.service.scoring;

import com.fantasy.bff.dto.response.PlayerProjection;
import java.util.Map;
import java.util.Set;

/**
 * One player as the ranking reads him: his line, and the little the league needs to know about
 * him besides — his name for the roster it prints, and the positions that decide which slot he
 * fills.
 *
 * @param playerId the platform's id, which the picks are made in
 * @param name shown on a roster row; never used to identify anyone
 * @param goalie whether the line is a goalie's, which decides the pool he is ranked against
 * @param positions eligible position codes (C/LW/RW/D for skaters, G for goalies)
 * @param scoring the scored stats, in the web's vocabulary
 * @param utility the stats that are only read, of which the ranking wants games played
 */
public record ScoredPlayer(
        int playerId,
        String name,
        boolean goalie,
        Set<String> positions,
        Map<String, Double> scoring,
        Map<String, Double> utility) {

    public static ScoredPlayer of(PlayerProjection row, String name, Set<String> positions) {
        boolean goalie = row.type() == PlayerProjection.Type.GOALIE;
        return new ScoredPlayer(
                row.playerId(),
                name,
                goalie,
                positions == null ? Set.of() : Set.copyOf(positions),
                row.stats() == null || row.stats().scoring() == null ? Map.of() : row.stats().scoring(),
                row.stats() == null || row.stats().utility() == null ? Map.of() : row.stats().utility());
    }

    /** The stat as the ranking reads it, or zero where the line does not carry it. */
    public double scoringValue(String key) {
        Double value = scoring.get(key);
        return value == null ? 0 : value;
    }

    /** Games played, which is what a goalie qualifies on in a category league. */
    public double gamesPlayed() {
        Double value = utility.get("gp");
        return value == null ? 0 : value;
    }

    /** The same player with his scored stats rounded to how the board is read. */
    public ScoredPlayer rounded(Map<String, Integer> decimals) {
        Map<String, Double> roundedScoring = new java.util.HashMap<>(scoring);
        for (String key : goalie ? ScoringStatKeys.GOALIE_SCORING : ScoringStatKeys.SKATER_SCORING) {
            double value = scoringValue(key);
            roundedScoring.put(key, JsNumbers.toFixed(value, decimals.getOrDefault(key, 0)));
        }
        return new ScoredPlayer(playerId, name, goalie, positions, Map.copyOf(roundedScoring), utility);
    }
}
