package com.fantasy.bff.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasy.bff.dto.response.RosterSlots;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The parts of the summary that are decisions rather than arithmetic: who starts where, night by
 * night, what a team's cells add up to, and what is left in the response for an account that has
 * not paid for the lines behind it. What each player is worth is held to the web's by
 * {@link LeagueSummaryGoldenVectorTest}.
 *
 * <p>Most cases play every club every night of an 82-night season with every line at 82 games,
 * so nobody misses a game and a lineup is the same every night: who starts is then exact, not a
 * share of a simulation.
 */
class LeagueSummaryCalculatorTest {

    private static final RosterSlots ONE_EACH = new RosterSlots(1, 1, 1, 0, 0, 1, 0, 0, 1);

    private final LeagueSummaryCalculator calculator = new LeagueSummaryCalculator();

    private static final LocalDate OPENING_NIGHT = LocalDate.of(2026, 10, 7);

    /** Every club of these plays every one of the nights, and nothing else. */
    private static LeagueSchedule everyNight(int nights, String... clubs) {
        List<LocalDate> dates = IntStream.range(0, nights).mapToObj(OPENING_NIGHT::plusDays).toList();
        Map<String, List<LocalDate>> window = new HashMap<>();
        Map<String, Integer> seasonGames = new HashMap<>();
        for (String club : clubs) {
            window.put(club, dates);
            seasonGames.put(club, nights);
        }
        return new LeagueSchedule(window, seasonGames);
    }

    private static final LeagueSchedule SEASON = everyNight(82, "TOR", "MTL");

    private static ScoredPlayer skater(int id, String name, Set<String> positions, double goals) {
        return skater(id, name, "TOR", positions, goals, 82);
    }

    private static ScoredPlayer skater(
            int id, String name, String club, Set<String> positions, double goals, double games) {
        return new ScoredPlayer(
                id, name, club, false, positions, Map.of("goals", goals, "assists", 0.0), Map.of("gp", games));
    }

    private static ScoredPlayer goalie(int id, String name, double wins) {
        return goalie(id, name, null, wins, 82);
    }

    private static ScoredPlayer goalie(int id, String name, String club, double wins, double games) {
        return new ScoredPlayer(
                id, name, club, true, Set.of("G"), Map.of("w", wins), Map.of("gp", games));
    }

    private static LeagueScoring pointsLeague(RosterSlots slots) {
        return LeagueScoring.of(
                true,
                new java.util.LinkedHashMap<>(Map.of("goals", 1.0)),
                List.of("goals"),
                slots,
                2,
                25,
                Map.of("goals", 0, "assists", 0, "w", 0));
    }

    @Test
    @DisplayName("totals a team by category and by lineup slot, and the two agree")
    void totalsAgree() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Centre", Set.of("C"), 40),
                skater(2, "Winger", Set.of("LW"), 30),
                goalie(3, "Keeper", 35));
        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2, 3))),
                pointsLeague(ONE_EACH), SEASON);

        LeagueSummary.Team team = summary.teams().get(0);
        assertThat(team.total()).isCloseTo(70, within(1e-9));
        assertThat(team.values().get("goals")).isCloseTo(70, within(1e-9));
        double bySlot = summary.positionKeys().stream()
                .mapToDouble(key -> team.values().getOrDefault(key, 0.0))
                .sum();
        assertThat(bySlot).as("the lineup accounts for the whole team").isCloseTo(70, within(1e-9));
    }

    @Test
    @DisplayName("teams come back best total first")
    void ranksTeams() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Best", Set.of("C"), 50),
                skater(2, "Worst", Set.of("C"), 10));
        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(
                        new LeagueSummaryCalculator.TeamPicks("weak", "Weak", false, List.of(2)),
                        new LeagueSummaryCalculator.TeamPicks("strong", "Strong", true, List.of(1))),
                pointsLeague(ONE_EACH), SEASON);

        assertThat(summary.teams()).extracting(LeagueSummary.Team::teamId)
                .containsExactly("strong", "weak");
    }

    /**
     * The point of the matching: a player eligible for two slots gives one up so that a player
     * eligible for only that slot can start. Placed best-first without it, the centre-winger
     * would take the centre slot and leave the pure centre on the bench.
     */
    @Test
    @DisplayName("a dual-position player yields his slot so more of the roster starts")
    void dualPositionPlayerYields() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Both", Set.of("C", "LW"), 50),
                skater(2, "Centre only", Set.of("C"), 40));
        RosterSlots slots = new RosterSlots(1, 1, 0, 0, 0, 0, 0, 0, 0);
        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2))),
                pointsLeague(slots), SEASON);

        LeagueSummary.Team team = summary.teams().get(0);
        assertThat(team.positionPlayers().get("LW")).extracting(LeagueSummary.Contributor::name)
                .containsExactly("Both");
        assertThat(team.positionPlayers().get("C")).extracting(LeagueSummary.Contributor::name)
                .containsExactly("Centre only");
    }

    private static LeagueSummary.Team oneTeam(List<ScoredPlayer> pool, RosterSlots slots) {
        List<Integer> ids = pool.stream().map(ScoredPlayer::playerId).toList();
        return calculator().summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, ids)),
                pointsLeague(slots), SEASON).teams().get(0);
    }

    private static LeagueSummaryCalculator calculator() {
        return new LeagueSummaryCalculator();
    }

    private static List<String> names(LeagueSummary.Team team, String column) {
        return team.positionPlayers().getOrDefault(column, List.of()).stream()
                .map(LeagueSummary.Contributor::name)
                .toList();
    }

    /**
     * A forwards-only league (nine F, five D, one Util): the F slots take forwards and nobody else,
     * so the second defenceman starts in Util rather than in a forward's slot.
     */
    @Test
    @DisplayName("a forward flex slot takes forwards only, and a defenceman starts in Util")
    void forwardFlexTakesForwardsOnly() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Top D", Set.of("D"), 50),
                skater(2, "Second D", Set.of("D"), 40),
                skater(3, "Centre", Set.of("C"), 30),
                skater(4, "Winger", Set.of("LW"), 20));
        RosterSlots slots = new RosterSlots(0, 0, 0, 0, 2, 1, 1, 0, 0);

        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2, 3, 4))),
                pointsLeague(slots), SEASON);

        LeagueSummary.Team team = summary.teams().get(0);
        assertThat(summary.positionKeys()).containsExactly("F", "D", "UTIL");
        assertThat(names(team, "F")).containsExactly("Centre", "Winger");
        assertThat(names(team, "D")).containsExactly("Top D");
        assertThat(names(team, "UTIL")).containsExactly("Second D");
    }

    @Test
    @DisplayName("a winger takes the wing flex before the forward flex, and a centre cannot")
    void wingBeforeForward() {
        LeagueSummary.Team team = oneTeam(List.of(
                        skater(1, "Top centre", Set.of("C"), 50),
                        skater(2, "Winger", Set.of("LW"), 40),
                        skater(3, "Second centre", Set.of("C"), 30)),
                new RosterSlots(1, 0, 0, 1, 1, 0, 0, 0, 0));

        assertThat(names(team, "C")).containsExactly("Top centre");
        assertThat(names(team, "W")).containsExactly("Winger");
        assertThat(names(team, "F")).containsExactly("Second centre");
    }

    /**
     * The wing flex is part of the matching, so a centre-winger moves to it to let a pure centre
     * start, where filling the flex from the leftovers would bench the centre.
     */
    @Test
    @DisplayName("a centre-winger moves to the wing flex so a pure centre can start")
    void dualPositionPlayerMovesToTheWingFlex() {
        LeagueSummary.Team team = oneTeam(List.of(
                        skater(1, "Both", Set.of("C", "LW"), 50),
                        skater(2, "Centre only", Set.of("C"), 40)),
                new RosterSlots(1, 0, 0, 1, 0, 0, 0, 0, 0));

        assertThat(names(team, "C")).containsExactly("Centre only");
        assertThat(names(team, "W")).containsExactly("Both");
    }

    @Test
    @DisplayName("the better of two wingers holds the named slot and the other the flex")
    void betterPlayerHoldsTheNamedSlot() {
        LeagueSummary.Team team = oneTeam(List.of(
                        skater(1, "Better", Set.of("LW"), 50),
                        skater(2, "Worse", Set.of("LW"), 40)),
                new RosterSlots(0, 1, 0, 1, 0, 0, 0, 0, 0));

        assertThat(names(team, "LW")).containsExactly("Better");
        assertThat(names(team, "W")).containsExactly("Worse");
    }

    @Test
    @DisplayName("a player with nowhere to start counts for nothing, and the bench has no column")
    void benchCountsForNothing() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Starter", Set.of("C"), 50),
                skater(2, "Spare", Set.of("C"), 40));
        RosterSlots slots = new RosterSlots(1, 1, 0, 0, 0, 0, 0, 3, 0);
        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2))),
                pointsLeague(slots), SEASON);

        LeagueSummary.Team team = summary.teams().get(0);
        assertThat(summary.positionKeys()).containsExactly("LW", "C");
        assertThat(team.total()).isCloseTo(50, within(1e-9));
        assertThat(team.roster()).extracting(LeagueSummary.RosterRow::name).containsExactly("Starter", "Spare");
        assertThat(team.roster().get(1).total()).isEqualTo(0);
        assertThat(team.roster().get(1).values().get("goals")).as("his own line is still his").isEqualTo(40);
    }

    @Test
    @DisplayName("a stat of the other kind is null on a roster row, not zero")
    void statsOfTheOtherKindAreNull() {
        List<ScoredPlayer> pool = List.of(skater(1, "Centre", Set.of("C"), 40), goalie(2, "Keeper", 35));
        LeagueScoring league = LeagueScoring.of(
                true,
                new java.util.LinkedHashMap<>(Map.of("goals", 1.0, "w", 2.0)),
                List.of("goals", "w"),
                ONE_EACH,
                2,
                25,
                Map.of("goals", 0, "w", 0));

        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2))),
                league,
                SEASON);

        LeagueSummary.RosterRow keeper = summary.teams().get(0).roster().stream()
                .filter(row -> row.name().equals("Keeper"))
                .findFirst()
                .orElseThrow();
        assertThat(keeper.values().get("goals")).as("a goalie scores no goals of his own").isNull();
        assertThat(keeper.values().get("w")).isEqualTo(35);
    }

    @Test
    @DisplayName("a team that drafted nobody is still a team, at nothing")
    void emptyTeam() {
        LeagueSummary summary = calculator.summarise(
                List.of(skater(1, "Centre", Set.of("C"), 40)),
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Empty", false, List.of())),
                pointsLeague(ONE_EACH), SEASON);

        assertThat(summary.teams()).hasSize(1);
        assertThat(summary.teams().get(0).total()).isEqualTo(0);
        assertThat(summary.teams().get(0).roster()).isEmpty();
    }

    /** A pick the pool does not carry is left out rather than counted as nothing. */
    @Test
    @DisplayName("a pick that is not in the pool is skipped")
    void unknownPickIsSkipped() {
        LeagueSummary summary = calculator.summarise(
                List.of(skater(1, "Centre", Set.of("C"), 40)),
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 999))),
                pointsLeague(ONE_EACH), SEASON);

        assertThat(summary.teams().get(0).roster()).hasSize(1);
        assertThat(summary.teams().get(0).total()).isCloseTo(40, within(1e-9));
    }

    /** The pool's positions are a set; a row names them the way a lineup is read. */
    @Test
    @DisplayName("a roster row carries the player's club and his positions in lineup order")
    void rosterRowCarriesClubAndPositions() {
        LeagueSummary summary = calculator.summarise(
                List.of(skater(1, "Winger", Set.of("RW", "C", "LW"), 40), goalie(2, "Keeper", 30)),
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2))),
                pointsLeague(ONE_EACH), SEASON);

        LeagueSummary.RosterRow winger = summary.teams().get(0).roster().get(0);
        assertThat(winger.team()).isEqualTo("TOR");
        assertThat(winger.positions()).containsExactly("C", "LW", "RW");

        LeagueSummary.RosterRow keeper = summary.teams().get(0).roster().get(1);
        assertThat(keeper.team()).as("a player the pool lists no club for").isNull();
        assertThat(keeper.positions()).containsExactly("G");
    }

    /**
     * The case the nightly lineup exists for: a team of forwards out-scores everyone on paper, but
     * a lineup starts one centre and one winger a night, and its D slot stands empty.
     */
    @Test
    @DisplayName("a team of forwards loses to a balanced one that out-scores it nowhere but in its lineup")
    void forwardsOnlyLosesToABalancedTeam() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Centre A", Set.of("C"), 40),
                skater(2, "Centre B", Set.of("C"), 38),
                skater(3, "Winger A", Set.of("LW"), 35),
                skater(4, "Winger B", Set.of("LW"), 33),
                skater(5, "Centre", Set.of("C"), 30),
                skater(6, "Winger", Set.of("LW"), 28),
                skater(7, "Defenceman", Set.of("D"), 20));
        RosterSlots slots = new RosterSlots(1, 1, 0, 0, 0, 1, 0, 2, 0);
        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(
                        new LeagueSummaryCalculator.TeamPicks("forwards", "Forwards", false, List.of(1, 2, 3, 4)),
                        new LeagueSummaryCalculator.TeamPicks("balanced", "Balanced", true, List.of(5, 6, 7))),
                pointsLeague(slots), SEASON);

        assertThat(summary.teams()).extracting(LeagueSummary.Team::teamId).containsExactly("balanced", "forwards");
        assertThat(summary.teams().get(0).total()).isCloseTo(78, within(1e-9));
        assertThat(summary.teams().get(1).total()).isCloseTo(75, within(1e-9));
        assertThat(summary.teams().get(1).values().get("D")).isEqualTo(0);
    }

    /** Alexander's example: two LW slots and three left wings who all play every night. */
    @Test
    @DisplayName("the third winger for two wing slots counts for nothing when the other two never miss")
    void theThirdWingerSits() {
        LeagueSummary.Team team = oneTeam(List.of(
                        skater(1, "Kaprizov", Set.of("LW"), 50),
                        skater(2, "Draisaitl", Set.of("C", "LW"), 45),
                        skater(3, "Benson", Set.of("LW"), 20)),
                new RosterSlots(0, 2, 0, 0, 0, 0, 0, 3, 0));

        assertThat(team.total()).isCloseTo(95, within(1e-9));
        assertThat(names(team, "LW")).containsExactly("Kaprizov", "Draisaitl");
        assertThat(team.roster()).filteredOn(row -> row.name().equals("Benson"))
                .extracting(LeagueSummary.RosterRow::total).containsExactly(0.0);
    }

    /**
     * Missed games are simulated: a star who plays half his club's games leaves the slot to the
     * man behind him the other half, and both count for what they start. The share is a
     * simulation's, so it is close to a half rather than exactly one.
     */
    @Test
    @DisplayName("the bench fills in for a star on the nights he misses")
    void theBenchFillsInForAMissingStar() {
        LeagueSummary.Team team = oneTeam(List.of(
                        skater(1, "Star", "TOR", Set.of("LW"), 60, 41),
                        skater(2, "Depth", "TOR", Set.of("LW"), 20, 82)),
                new RosterSlots(0, 1, 0, 0, 0, 0, 0, 1, 0));

        LeagueSummary.RosterRow star = team.roster().get(0);
        LeagueSummary.RosterRow depth = team.roster().get(1);
        assertThat(star.name()).isEqualTo("Star");
        assertThat(star.total()).as("he starts every game he plays").isCloseTo(60, within(1e-9));
        assertThat(depth.total()).as("about half his games").isCloseTo(10, within(0.5));
    }

    @Test
    @DisplayName("two players for one slot both count in full when their clubs play on different nights")
    void differentNightsShareASlot() {
        List<LocalDate> even = IntStream.range(0, 41).mapToObj(day -> OPENING_NIGHT.plusDays(2L * day)).toList();
        List<LocalDate> odd = IntStream.range(0, 41).mapToObj(day -> OPENING_NIGHT.plusDays(2L * day + 1)).toList();
        LeagueSchedule alternating = new LeagueSchedule(
                Map.of("TOR", even, "MTL", odd), Map.of("TOR", 41, "MTL", 41));
        List<ScoredPlayer> pool = List.of(
                skater(1, "Leaf", "TOR", Set.of("LW"), 30, 41),
                skater(2, "Hab", "MTL", Set.of("LW"), 25, 41));

        LeagueSummary.Team team = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2))),
                pointsLeague(new RosterSlots(0, 1, 0, 0, 0, 0, 0, 1, 0)),
                alternating).teams().get(0);

        assertThat(team.total()).isCloseTo(55, within(1e-9));
    }

    /**
     * A club has one start a night. Two of its goalies, each starting half its games, never draw
     * the same night, so a team holding both starts one of them every night in its one G slot.
     */
    @Test
    @DisplayName("a club's two goalies never start the same night, so one G slot holds them both")
    void aClubsGoaliesShareItsStarts() {
        List<ScoredPlayer> pool = List.of(
                goalie(1, "Starter", "TOR", 20, 41),
                goalie(2, "Backup", "TOR", 18, 41));
        LeagueScoring league = LeagueScoring.of(
                true,
                new java.util.LinkedHashMap<>(Map.of("w", 2.0)),
                List.of("w"),
                new RosterSlots(0, 0, 0, 0, 0, 0, 0, 1, 1),
                2,
                25,
                Map.of("goals", 0, "w", 0));

        LeagueSummary.Team team = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2))),
                league,
                SEASON).teams().get(0);

        assertThat(team.total()).isCloseTo(76, within(1e-9));
    }

    @Test
    @DisplayName("a player whose club has no games left counts for nothing")
    void noGamesCountsNothing() {
        LeagueSummary.Team team = oneTeam(List.of(
                        skater(1, "Centre", Set.of("C"), 40),
                        skater(2, "Nowhere", "SEA", Set.of("LW"), 30, 82),
                        skater(3, "Unsigned", null, Set.of("RW"), 20, 82)),
                ONE_EACH);

        assertThat(team.total()).isCloseTo(40, within(1e-9));
        assertThat(team.roster()).hasSize(3);
    }

    /** The draws are a hash of the run, the player and the night: a league reads the same twice. */
    @Test
    @DisplayName("a simulated league reads the same every time")
    void readsTheSameTwice() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Star", "TOR", Set.of("LW"), 60, 50),
                skater(2, "Depth", "MTL", Set.of("LW"), 20, 70),
                skater(3, "Third", "TOR", Set.of("LW"), 10, 60));
        RosterSlots slots = new RosterSlots(0, 1, 0, 0, 0, 0, 0, 2, 0);

        assertThat(oneTeam(pool, slots).total()).isEqualTo(oneTeam(pool, slots).total());
    }

    /**
     * Alexander's example: a roster of two (one LW, one bench) carrying a third man on injured
     * reserve. TOR and MTL play on alternate nights, so a third left wing would start every night
     * the others do not, a depth no league allows: to activate him a team drops someone. The
     * parked man is the best of the three, so he counts and the weakest healthy one is cut.
     */
    @Test
    @DisplayName("a team counts its best players, as many as its roster holds, parked ones included")
    void theRosterHoldsItsBestPlayers() {
        List<LocalDate> even = IntStream.range(0, 41).mapToObj(day -> OPENING_NIGHT.plusDays(2L * day)).toList();
        List<LocalDate> odd = IntStream.range(0, 41).mapToObj(day -> OPENING_NIGHT.plusDays(2L * day + 1)).toList();
        LeagueSchedule alternating = new LeagueSchedule(
                Map.of("TOR", even, "MTL", odd), Map.of("TOR", 41, "MTL", 41));
        List<ScoredPlayer> pool = List.of(
                skater(1, "Leaf", "TOR", Set.of("LW"), 30, 41),
                skater(2, "Hab", "MTL", Set.of("LW"), 25, 41),
                skater(3, "Parked", "TOR", Set.of("LW"), 40, 41));

        LeagueSummary.Team team = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2, 3), Map.of(3, "IR"))),
                pointsLeague(new RosterSlots(0, 1, 0, 0, 0, 0, 0, 1, 0)),
                alternating).teams().get(0);

        // Parked starts every TOR night over Leaf; Hab, cut, no longer fills the MTL nights.
        assertThat(team.total()).isCloseTo(40, within(1e-9));
        assertThat(team.roster()).extracting(LeagueSummary.RosterRow::name)
                .containsExactly("Parked", "Leaf", "Hab");
        assertThat(team.roster()).extracting(LeagueSummary.RosterRow::reserveSlot).containsExactly("IR", null, null);
        assertThat(team.roster()).extracting(LeagueSummary.RosterRow::counted).containsExactly(true, true, false);
        assertThat(team.roster().get(2).total()).as("cut, he counts for nothing").isEqualTo(0);
        assertThat(team.roster().get(2).fullValue()).as("but is still worth his own value").isCloseTo(25, within(1e-9));
    }

    @Test
    @DisplayName("the cut passes over a goalie the G slot needs, however little he is worth")
    void theCutKeepsTheGoalieTheLineupNeeds() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Centre", Set.of("C"), 50),
                goalie(2, "Backup", "TOR", 5, 82),
                skater(3, "Spare", Set.of("C"), 30));
        LeagueScoring league = LeagueScoring.of(
                true,
                new java.util.LinkedHashMap<>(Map.of("goals", 1.0, "w", 1.0)),
                List.of("goals", "w"),
                new RosterSlots(1, 0, 0, 0, 0, 0, 0, 0, 1),
                2,
                25,
                Map.of("goals", 0, "assists", 0, "w", 0));

        LeagueSummary.Team team = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2, 3))),
                league,
                SEASON).teams().get(0);

        assertThat(team.total()).isCloseTo(55, within(1e-9));
        assertThat(team.roster()).filteredOn(LeagueSummary.RosterRow::counted)
                .extracting(LeagueSummary.RosterRow::name).containsExactlyInAnyOrder("Centre", "Backup");
    }

    @Test
    @DisplayName("a team within its roster counts everyone")
    void aTeamWithinItsRosterCountsEveryone() {
        LeagueSummary.Team team = oneTeam(List.of(
                        skater(1, "Starter", Set.of("C"), 50),
                        skater(2, "Spare", Set.of("C"), 40)),
                new RosterSlots(1, 0, 0, 0, 0, 0, 0, 1, 0));

        assertThat(team.roster()).allSatisfy(row -> {
            assertThat(row.counted()).isTrue();
            assertThat(row.reserveSlot()).isNull();
        });
    }
}
