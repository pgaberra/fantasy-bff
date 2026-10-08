package com.fantasy.bff.service.mapping;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the model's units differ from the app's stat vocabulary, the conversion lives here, so a
 * season line and a line over a stretch cannot come out in different units. A missing value is
 * left out rather than zeroed.
 */
public final class ModelStatMapping {

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    /**
     * The app's stat keys each of the model's per-game worths stands for: the key the model's own
     * stat is served under, and the app's sums of it. A special-teams sum is read at its power-play
     * part's worth, which is nearly all of it, and a defenceman's points at the points' worth.
     */
    private static final Map<String, List<String>> WORTH_KEYS = Map.ofEntries(
            Map.entry("goals", List.of("goals")),
            Map.entry("assists", List.of("assists")),
            Map.entry("points", List.of("points", "defPoints")),
            Map.entry("pim", List.of("pim")),
            Map.entry("pp_goals", List.of("ppg", "stpg")),
            Map.entry("pp_assists", List.of("ppa", "stpa")),
            Map.entry("pp_points", List.of("ppp", "stp")),
            Map.entry("sh_goals", List.of("shg")),
            Map.entry("sh_assists", List.of("sha")),
            Map.entry("sh_points", List.of("shp")),
            Map.entry("gw_goals", List.of("gwg")),
            Map.entry("shots", List.of("sog")),
            Map.entry("hits", List.of("hits")),
            Map.entry("blocks", List.of("blocks")),
            Map.entry("faceoffs_won", List.of("fw")),
            Map.entry("faceoffs_lost", List.of("fl")),
            Map.entry("hat_tricks", List.of("hatTricks")),
            Map.entry("shifts", List.of("shifts")),
            Map.entry("wins", List.of("w")),
            Map.entry("losses", List.of("l")),
            Map.entry("ot_losses", List.of("otl")),
            Map.entry("shutouts", List.of("sho")),
            Map.entry("shots_against", List.of("sa")),
            Map.entry("saves", List.of("sv")),
            Map.entry("goals_against", List.of("ga")));

    private ModelStatMapping() {
    }

    /**
     * A game's worth to each stat, from the model's stat names to the app's keys. A worth the model
     * names a stat the app does not serve is left out, and so is a missing one: the page reads an
     * absent key as an average night.
     */
    public static Map<String, Double> statWorth(Map<String, BigDecimal> byModelStat) {
        Map<String, Double> worth = new LinkedHashMap<>();
        if (byModelStat == null) {
            return worth;
        }
        byModelStat.forEach((stat, value) -> {
            if (value != null) {
                WORTH_KEYS.getOrDefault(stat, List.of())
                        .forEach(key -> worth.put(key, value.doubleValue()));
            }
        });
        return worth;
    }

    /** The model works in a fraction; the app's column is a percentage. */
    public static void putPercent(Map<String, Double> target, String key, BigDecimal fraction) {
        if (fraction != null) {
            target.put(key, fraction.multiply(PERCENT).doubleValue());
        }
    }

    /** The model reports a per-game figure; the column is the total over the games played. */
    public static void putTotal(
            Map<String, Double> target, String key, BigDecimal perGame, BigDecimal games) {
        if (perGame != null && games != null) {
            target.put(key, perGame.multiply(games).doubleValue());
        }
    }

    /**
     * Share of decisions won. An overtime loss is a decision like any other, so a goalie who
     * only ever loses past regulation has a win percentage of nought rather than none. Rounded
     * to the three decimals the column carries, as the split endpoint rounds it.
     */
    public static void putWinPct(
            Map<String, Double> target, BigDecimal wins, BigDecimal losses, BigDecimal otLosses) {
        if (wins == null || losses == null || otLosses == null) {
            return;
        }
        double decisions = wins.doubleValue() + losses.doubleValue() + otLosses.doubleValue();
        if (decisions > 0) {
            target.put("winPct", PlayerFieldMapping.round3(wins.doubleValue() / decisions));
        }
    }
}
