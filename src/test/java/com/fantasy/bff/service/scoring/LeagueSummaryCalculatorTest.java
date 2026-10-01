package com.fantasy.bff.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasy.bff.dto.response.RosterSlots;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The parts of the summary that are decisions rather than arithmetic: who starts where, what a
 * team's cells add up to, and what is left in the response for an account that has not paid for
 * the lines behind it. The arithmetic itself is held to the web's by
 * {@link LeagueSummaryGoldenVectorTest}.
 */
class LeagueSummaryCalculatorTest {

    private static final RosterSlots ONE_EACH = new RosterSlots(1, 1, 1, 0, 0, 1, 0, 0, 1);

    private final LeagueSummaryCalculator calculator = new LeagueSummaryCalculator();

    private static ScoredPlayer skater(int id, String name, Set<String> positions, double goals) {
        return new ScoredPlayer(
                id, name, "TOR", false, positions, Map.of("goals", goals, "assists", 0.0), Map.of("gp", 82.0));
    }

    private static ScoredPlayer goalie(int id, String name, double wins) {
        return new ScoredPlayer(
                id, name, null, true, Set.of("G"), Map.of("w", wins), Map.of("gp", 60.0));
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
                pointsLeague(ONE_EACH));

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
                pointsLeague(ONE_EACH));

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
                pointsLeague(slots));

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
                pointsLeague(slots)).teams().get(0);
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
                pointsLeague(slots));

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
        assertThat(names(team, "BN")).isEmpty();
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
    @DisplayName("a player with nowhere to start falls to the bench, which then has a column")
    void benchAppearsForTheUnplaced() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Starter", Set.of("C"), 50),
                skater(2, "Spare", Set.of("C"), 40));
        RosterSlots slots = new RosterSlots(1, 1, 0, 0, 0, 0, 0, 0, 0);
        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2))),
                pointsLeague(slots));

        assertThat(summary.positionKeys()).containsExactly("LW", "C", "BN");
        assertThat(summary.teams().get(0).positionPlayers().get("BN"))
                .extracting(LeagueSummary.Contributor::name).containsExactly("Spare");
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
                league);

        LeagueSummary.RosterRow keeper = summary.teams().get(0).roster().stream()
                .filter(row -> row.name().equals("Keeper"))
                .findFirst()
                .orElseThrow();
        assertThat(keeper.values().get("goals")).as("a goalie scores no goals of his own").isNull();
        assertThat(keeper.values().get("w")).isEqualTo(35);
    }

    /** What a free account is handed: the totals, and nothing that says how they were reached. */
    @Test
    @DisplayName("the aggregates survive on their own, with every per-player half dropped")
    void aggregatesOnlyDropsThePlayers() {
        List<ScoredPlayer> pool = List.of(skater(1, "Centre", Set.of("C"), 40));
        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1))),
                pointsLeague(ONE_EACH)).aggregatesOnly();

        LeagueSummary.Team team = summary.teams().get(0);
        assertThat(team.total()).isCloseTo(40, within(1e-9));
        assertThat(team.values()).isNotEmpty();
        assertThat(team.roster()).isNull();
        assertThat(team.positionPlayers()).isNull();
    }

    @Test
    @DisplayName("a team that drafted nobody is still a team, at nothing")
    void emptyTeam() {
        LeagueSummary summary = calculator.summarise(
                List.of(skater(1, "Centre", Set.of("C"), 40)),
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Empty", false, List.of())),
                pointsLeague(ONE_EACH));

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
                pointsLeague(ONE_EACH));

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
                pointsLeague(ONE_EACH));

        LeagueSummary.RosterRow winger = summary.teams().get(0).roster().get(0);
        assertThat(winger.team()).isEqualTo("TOR");
        assertThat(winger.positions()).containsExactly("C", "LW", "RW");

        LeagueSummary.RosterRow keeper = summary.teams().get(0).roster().get(1);
        assertThat(keeper.team()).as("a player the pool lists no club for").isNull();
        assertThat(keeper.positions()).containsExactly("G");
    }

    /**
     * The rule the rankings count by: a team's best players, as many as the league plays. The
     * injured star still counts — the projection rates him above the man picked up for him — and
     * the pickup is the one who drops out, whatever slot either holds in Yahoo.
     */
    @Test
    @DisplayName("an injured star the projection rates highest is kept and a lesser pickup drops out")
    void injuredStarIsKeptAndThePickupDrops() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Injured star", Set.of("C"), 60),
                skater(2, "Winger", Set.of("LW"), 30),
                skater(3, "Depth centre", Set.of("C"), 25),
                skater(4, "Pickup", Set.of("C"), 20));
        RosterSlots slots = new RosterSlots(1, 1, 0, 0, 0, 0, 0, 1, 0);
        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2, 3, 4))),
                pointsLeague(slots));

        LeagueSummary.Team team = summary.teams().get(0);
        assertThat(team.roster()).extracting(LeagueSummary.RosterRow::name)
                .containsExactly("Injured star", "Winger", "Depth centre");
        assertThat(team.total()).isCloseTo(115, within(1e-9));
        assertThat(team.values().get("goals")).isCloseTo(115, within(1e-9));
        assertThat(team.positionPlayers().values().stream().flatMap(List::stream))
                .extracting(LeagueSummary.Contributor::name)
                .doesNotContain("Pickup");
    }

    /**
     * Picked by value alone: with two spots the two best count even when both are centres, and
     * the lineup then places them as it would any roster — one starts, one sits.
     */
    @Test
    @DisplayName("a team holding more than the league plays shows exactly that many, by value")
    void aLargeRosterShowsExactlyTheCount() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "First", Set.of("C"), 50),
                skater(2, "Second", Set.of("C"), 45),
                skater(3, "Winger", Set.of("LW"), 10),
                skater(4, "Fourth", Set.of("C"), 5),
                skater(5, "Fifth", Set.of("RW"), 4));
        RosterSlots slots = new RosterSlots(1, 1, 0, 0, 0, 0, 0, 0, 0);
        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(5, 4, 3, 2, 1))),
                pointsLeague(slots));

        LeagueSummary.Team team = summary.teams().get(0);
        assertThat(team.roster()).extracting(LeagueSummary.RosterRow::name).containsExactly("First", "Second");
        assertThat(team.positionPlayers().values().stream().mapToInt(List::size).sum()).isEqualTo(2);
        assertThat(team.positionPlayers().get("C")).extracting(LeagueSummary.Contributor::name)
                .containsExactly("First");
        assertThat(team.positionPlayers().get("BN")).extracting(LeagueSummary.Contributor::name)
                .containsExactly("Second");
        assertThat(team.total()).isCloseTo(95, within(1e-9));
    }

    @Test
    @DisplayName("a team holding fewer than the league plays counts everyone")
    void aShortRosterCountsEveryone() {
        List<ScoredPlayer> pool = List.of(
                skater(1, "Centre", Set.of("C"), 40),
                skater(2, "Winger", Set.of("LW"), 30),
                skater(3, "Spare", Set.of("C"), 5));
        RosterSlots slots = new RosterSlots(1, 1, 0, 0, 0, 0, 0, 2, 0);
        LeagueSummary summary = calculator.summarise(
                pool,
                List.of(new LeagueSummaryCalculator.TeamPicks("t1", "Mine", true, List.of(1, 2, 3))),
                pointsLeague(slots));

        LeagueSummary.Team team = summary.teams().get(0);
        assertThat(team.roster()).hasSize(3);
        assertThat(team.total()).isCloseTo(75, within(1e-9));
    }
}
