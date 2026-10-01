package com.fantasy.bff.service.mapping;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Where the model's units differ from the app's stat vocabulary, the conversion lives here, so a
 * season line and a line over a stretch cannot come out in different units. A missing value is
 * left out rather than zeroed.
 */
public final class ModelStatMapping {

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private ModelStatMapping() {
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
