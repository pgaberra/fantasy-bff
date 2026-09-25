package com.fantasy.bff.service.scoring;

import com.fantasy.bff.dto.response.DraftSettings;
import com.fantasy.bff.dto.response.ProjectionSettings;
import com.fantasy.bff.dto.response.RosterSlots;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a league scores, as the ranking needs it: the wire, the categories it counts, and the two
 * pools a category z-score is measured against.
 *
 * @param points whether the league counts fantasy points rather than categories
 * @param statWeights what each stat pays, in the order the league gave them — floating-point
 *     addition is not associative, so the order is part of the answer
 * @param activeScoringColumns the stats the league counts, in the league's own order, which is
 *     the order its columns are read in
 * @param activeScoringSet the same stats to ask about one at a time, which the ranking does once
 *     per stat per player
 * @param rosterSlots what a team starts, which decides both pool sizes and the lineup
 * @param leagueSize how many teams draft
 * @param minGoalieGames games a goalie must be projected to qualify in a category league
 * @param decimals how the board's numbers are written, or null to derive them from the rows
 */
public record LeagueScoring(
        boolean points,
        Map<String, Double> statWeights,
        List<String> activeScoringColumns,
        Set<String> activeScoringSet,
        RosterSlots rosterSlots,
        int leagueSize,
        int minGoalieGames,
        Map<String, Integer> decimals) {

    /** What a league falls back to where it names no size, as the web's defaults do. */
    private static final int DEFAULT_LEAGUE_SIZE = 12;

    private static final int DEFAULT_MIN_GOALIE_GAMES = 25;

    /** What a team counts where the league's slots add up to nothing: a standard Yahoo roster. */
    static final int DEFAULT_COUNTED_PLAYERS = 16;

    public LeagueScoring {
        statWeights = statWeights == null ? Map.of() : new LinkedHashMap<>(statWeights);
        activeScoringColumns = activeScoringColumns == null ? List.of() : List.copyOf(activeScoringColumns);
        activeScoringSet = activeScoringSet == null ? Set.copyOf(activeScoringColumns) : Set.copyOf(activeScoringSet);
        decimals = decimals == null ? null : Map.copyOf(decimals);
    }

    public static LeagueScoring of(
            boolean points,
            Map<String, Double> statWeights,
            List<String> activeScoringColumns,
            RosterSlots rosterSlots,
            int leagueSize,
            int minGoalieGames,
            Map<String, Integer> decimals) {
        return new LeagueScoring(
                points, statWeights, activeScoringColumns, null, rosterSlots, leagueSize,
                minGoalieGames, decimals);
    }

    public static LeagueScoring from(DraftSettings league) {
        return of(
                league.scoringType() != ProjectionSettings.ScoringType.CATEGORY,
                league.statWeights(),
                league.activeScoringColumns(),
                league.rosterSlots(),
                league.leagueSize() == null ? DEFAULT_LEAGUE_SIZE : league.leagueSize(),
                league.minGoalieGames() == null ? DEFAULT_MIN_GOALIE_GAMES : league.minGoalieGames(),
                null);
    }

    /**
     * How many skaters the league drafts: every skater slot, bench included, across every team.
     * That is the pool a category z-score is measured against — being above average among the
     * players who are actually drafted is what a category league pays for.
     */
    public int skaterPoolSize() {
        RosterSlots slots = rosterSlots;
        return leagueSize
                * (slots.c() + slots.lw() + slots.rw() + slots.d() + slots.util() + slots.bn());
    }

    /** How many goalies the league drafts. */
    public int goaliePoolSize() {
        return leagueSize * rosterSlots.g();
    }

    /**
     * How many of a team's players count towards its totals: every roster spot the league plays,
     * starters and bench, but no injured-reserve spot. A team carrying injured players and their
     * replacements holds more than that, and counting them all would credit it for players no
     * lineup can hold. The league's settings leave IR, IR+ and NA out of the slots, so the slots
     * add up to exactly this.
     */
    public int countedPlayers() {
        RosterSlots slots = rosterSlots;
        int spots = slots.c() + slots.lw() + slots.rw() + slots.d() + slots.util() + slots.bn() + slots.g();
        return spots > 0 ? spots : DEFAULT_COUNTED_PLAYERS;
    }
}
