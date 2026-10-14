package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.ScoutLine;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * An earlier line of the model's brought onto today's footing, so the two can be read side by side.
 *
 * <p>A line is its per-game rates over the games it expected him to play, and the games it
 * expected are a share of the games his club had left on the day it was made. A line from a
 * month ago covers games that have since been played, so its totals are not comparable with
 * today's rest of the season; its rates and its share are. The old line is therefore re-spread
 * over the games his club has left today: the share of the schedule it expected him to play, of
 * today's schedule, at its own rates. A player the old line expected to miss a third of the
 * season still misses a third of what is left; a player it had at 14 minutes a night still has
 * them.
 *
 * <p>Alexander's own description of the fix: take the projection from then and adjust it so it
 * starts from the same place as today's, with the games left today.
 */
final class ScoutRescale {

    /**
     * The stats on a {@link ScoutLine} that are rates rather than totals: per game or per shot, so
     * they carry over as they are. Everything else scales with the games.
     */
    static final Set<String> RATES = Set.of("toiPerGame", "shPct", "svPct", "gaa", "winPct");

    private ScoutRescale() {}

    /**
     * {@code then} re-spread over {@code clubGamesNow}, given the {@code clubGamesThen} it was spread
     * over. Null for a line with no games, or no schedule, behind it: there is no rate to carry.
     */
    static ScoutLine toGamesLeft(ScoutLine then, double clubGamesThen, int clubGamesNow) {
        if (then == null || then.games() <= 0 || clubGamesThen <= 0 || clubGamesNow < 0) {
            return null;
        }
        double games = then.games() / clubGamesThen * clubGamesNow;
        double factor = games / then.games();
        Map<String, Double> stats = new LinkedHashMap<>();
        then.stats().forEach((key, value) -> stats.put(key, RATES.contains(key) ? value : value * factor));
        return new ScoutLine(games, stats);
    }
}
