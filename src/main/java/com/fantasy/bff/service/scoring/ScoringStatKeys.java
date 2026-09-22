package com.fantasy.bff.service.scoring;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stat vocabulary a league is scored in, as the web defines it.
 *
 * <p>This is a port: the same lists live in {@code fantasy-web/src/app/models/stat-key.model.ts}
 * and the same defaults in {@code projection-settings-section/model.ts}. Both sides score the
 * same boards, so they have to agree to the last decimal — which is what the golden vectors in
 * {@code src/test/resources/scoring/} hold them to. Change one side and the other's test fails.
 */
public final class ScoringStatKeys {

    /** Scored stats a skater's line carries, in the order the web lists them. */
    public static final List<String> SKATER_SCORING = List.of(
            "goals", "assists", "points", "plusMinus", "pim", "ppg", "ppa", "ppp", "shg", "sha",
            "shp", "stpg", "stpa", "stp", "gwg", "hatTricks", "sog", "shPct", "fw", "fl", "hits",
            "blocks", "defPoints", "shifts", "toi");

    /** Scored stats a goalie's line carries. Time on ice is scored for both kinds. */
    public static final List<String> GOALIE_SCORING = List.of(
            "gs", "w", "l", "otl", "sho", "sa", "sv", "ga", "gaa", "svPct", "winPct", "toi");

    public static final Set<String> SKATER_SCORING_SET = Set.copyOf(SKATER_SCORING);

    public static final Set<String> GOALIE_SCORING_SET = Set.copyOf(GOALIE_SCORING);

    /** Stats where the lower number is the better one. */
    public static final Set<String> LOWER_IS_BETTER = Set.of("ga", "gaa", "l");

    /** Stats that describe a per-unit share rather than a running total. */
    public static final Set<String> RATE_STATS = Set.of("shPct", "svPct", "winPct", "gaa");

    /**
     * How many decimals a column is written in before anyone changes it. A board whose numbers
     * are whole is read as whole; the model's fractional lines earn a decimal place through
     * {@link #readableDecimals}, exactly as the editor reads them.
     */
    public static final Map<String, Integer> DEFAULT_DECIMALS = Map.ofEntries(
            Map.entry("gp", 0),
            Map.entry("goals", 0),
            Map.entry("assists", 0),
            Map.entry("points", 0),
            Map.entry("plusMinus", 0),
            Map.entry("pim", 0),
            Map.entry("ppg", 0),
            Map.entry("ppa", 0),
            Map.entry("ppp", 0),
            Map.entry("shg", 0),
            Map.entry("sha", 0),
            Map.entry("shp", 0),
            Map.entry("stpg", 0),
            Map.entry("stpa", 0),
            Map.entry("stp", 0),
            Map.entry("gwg", 0),
            // The only counting stat most of the league holds fewer than one of over a season:
            // at no decimals the model's column reads 0 for nine skaters in ten.
            Map.entry("hatTricks", 1),
            Map.entry("sog", 0),
            Map.entry("shPct", 1),
            Map.entry("fw", 0),
            Map.entry("fl", 0),
            Map.entry("hits", 0),
            Map.entry("blocks", 0),
            Map.entry("defPoints", 0),
            Map.entry("shifts", 0),
            Map.entry("toi", 0),
            Map.entry("gs", 0),
            Map.entry("w", 0),
            Map.entry("l", 0),
            Map.entry("otl", 0),
            Map.entry("sho", 0),
            Map.entry("sa", 0),
            Map.entry("sv", 0),
            Map.entry("ga", 0),
            Map.entry("gaa", 2),
            Map.entry("svPct", 3),
            Map.entry("winPct", 3));

    /** Written mm:ss, so a decimal place on it means nothing. */
    private static final Set<String> NOT_DECIMAL_STATS = Set.of("toi");

    private static final int FRACTIONAL_DECIMALS = 1;

    private ScoringStatKeys() {
    }

    /**
     * The decimals a board is actually read with: the defaults, except that a column holding
     * fractions is given a decimal place. The AI projection is why this exists — its lines are
     * fractional throughout, and at the plain defaults 49.4 goals would be scored as 49, since
     * the ranking deliberately scores what the table shows.
     *
     * @param pool every row the board holds
     * @return the decimals per stat key, defaults where a column holds only whole numbers
     */
    public static Map<String, Integer> readableDecimals(Iterable<ScoredPlayer> pool) {
        Map<String, Integer> derived = new java.util.HashMap<>(DEFAULT_DECIMALS);
        for (ScoredPlayer player : pool) {
            for (Map<String, Double> stats : List.of(player.scoring(), player.utility())) {
                for (Map.Entry<String, Double> stat : stats.entrySet()) {
                    String key = stat.getKey();
                    Integer held = derived.get(key);
                    if (held == null || held >= FRACTIONAL_DECIMALS || NOT_DECIMAL_STATS.contains(key)) {
                        continue;
                    }
                    if (isFractional(stat.getValue())) {
                        derived.put(key, FRACTIONAL_DECIMALS);
                    }
                }
            }
        }
        return Map.copyOf(derived);
    }

    private static boolean isFractional(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return false;
        }
        return Math.abs(value - Math.round(value)) > 1e-9;
    }
}
