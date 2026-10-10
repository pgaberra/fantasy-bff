package com.fantasy.bff.service.scoring;

import com.fantasy.bff.dto.response.RosterSlots;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Adds a league up: what every team's lineup will start, night by night over the schedule, by
 * category and by slot.
 *
 * <p>A player counts for the share of his games his team's best lineup starts him in
 * ({@link NightlyLineups}): a bench player only on the nights he fills a hole, and a team of
 * forwards gets nothing from the D and G slots it leaves empty. Before this a team was its best N
 * players at their whole value, positions aside (fantasy-bff#363 replaced fantasy-bff#317).
 *
 * <p>The lineup is picked from the team's best players, as many as its roster holds, starters and
 * bench: a team carrying more (players parked on injured reserve or not-active, which take no
 * roster spot) would have to drop the extra to play them, so they are not a deeper bench. A
 * player out a week still counts where he is among the best, and the weakest healthy one sits
 * out instead (Alexander's call, 2026-10-10).
 *
 * <p>What each player is worth is the same arithmetic as the web's Draft Mode table
 * ({@code fantasy-web/src/app/draft-mode/league-projection.ts}), held to it player by player by
 * {@code LeagueSummaryGoldenVectorTest}. How much of it a team gets is this side's alone: the web
 * still adds a drafted roster up whole.
 */
@Component
public class LeagueSummaryCalculator {

    /** The position column for a team's bench: everyone its best lineup leaves out. */
    static final String BENCH = "BN";

    /**
     * One team as it stands: who it is, and the players it holds, in pick order.
     *
     * @param reserve those of its players parked today in an injured-reserve or not-active slot, each
     *     with that slot's code
     */
    public record TeamPicks(
            String teamId, String name, boolean mine, List<Integer> playerIds, Map<Integer, String> reserve) {

        public TeamPicks {
            reserve = reserve == null ? Map.of() : Map.copyOf(reserve);
        }

        /** A team nobody has parked anyone for: a draft's, or one read before the season. */
        public TeamPicks(String teamId, String name, boolean mine, List<Integer> playerIds) {
            this(teamId, name, mine, playerIds, Map.of());
        }
    }

    /**
     * Totals a league.
     *
     * @param pool every row the ranking is computed over, held or not — a category z-score is
     *     measured against the whole pool, so a summary of the held players alone would score
     *     them against each other and say nothing
     * @param teams the teams and their players
     * @param league how the league scores and what it starts
     * @param schedule the nights each club plays over the stretch being ranked
     * @return the teams, best total first, with the per-player halves filled in
     */
    public LeagueSummary summarise(
            List<ScoredPlayer> pool, List<TeamPicks> teams, LeagueScoring league, LeagueSchedule schedule) {
        ProjectionScoring.Scores scores = ProjectionScoring.score(pool, league);
        Map<Integer, ScoredPlayer> byId = new HashMap<>();
        for (ScoredPlayer player : pool) {
            byId.putIfAbsent(player.playerId(), player);
        }

        // The league's own order, which is the order its columns are read in.
        List<String> categoryKeys = league.activeScoringColumns();
        RosterSlots lineup = league.lineup();
        List<String> positionKeys = new ArrayList<>(Arrays.stream(LineupSlot.values())
                .filter(slot -> slot.count(lineup) > 0)
                .map(LineupSlot::column)
                .toList());
        if (lineup.bn() > 0) {
            positionKeys.add(BENCH);
        }
        Ranking ranking = new Ranking(
                scores, categoryKeys, positionKeys, NightlyLineups.seats(lineup), schedule,
                creaseOffsets(teams, byId, schedule), lineup);

        List<LeagueSummary.Team> rows = teams.stream()
                .map(team -> row(team, byId, ranking))
                .sorted(Comparator.comparingDouble(LeagueSummary.Team::total).reversed())
                .toList();

        return new LeagueSummary(categoryKeys, positionKeys, rows);
    }

    /** What every team in the league is totalled against. */
    private record Ranking(
            ProjectionScoring.Scores scores,
            List<String> categoryKeys,
            List<String> positionKeys,
            List<LineupSlot> seats,
            LeagueSchedule schedule,
            Map<Integer, Double> creaseOffsets,
            RosterSlots lineup) {
    }

    private LeagueSummary.Team row(TeamPicks team, Map<Integer, ScoredPlayer> byId, Ranking league) {
        ProjectionScoring.Scores scores = league.scores();
        List<ScoredPlayer> held = team.playerIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        Set<Integer> counted = counted(held, scores, league.lineup());
        List<NightlyLineups.Player> lineupPlayers = held.stream()
                .filter(player -> counted.contains(player.playerId()))
                .map(player -> new NightlyLineups.Player(
                        player.playerId(),
                        player.goalie(),
                        player.positions(),
                        player.team(),
                        perGame(player, scores, league.schedule()),
                        availability(player, league.schedule()),
                        league.creaseOffsets().getOrDefault(player.playerId(), 0.0)))
                .toList();
        Map<Integer, Map<LineupSlot, Double>> starts =
                NightlyLineups.starts(lineupPlayers, league.seats(), league.schedule());
        Map<Integer, Double> shares = new HashMap<>();
        for (ScoredPlayer player : held) {
            shares.put(player.playerId(), starts.getOrDefault(player.playerId(), Map.of()).values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum());
        }

        double total = held.stream()
                .mapToDouble(player -> value(scores, player) * shares.get(player.playerId()))
                .sum();

        // A category cell aggregates only the players the stat applies to — a goalie contributes
        // no hits, a skater no saves — so each column sums over its own side of the roster.
        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, Double> fullValues = new LinkedHashMap<>();
        for (String key : league.categoryKeys()) {
            boolean wantsSkater = ScoringStatKeys.SKATER_SCORING_SET.contains(key);
            Predicate<ScoredPlayer> ofThisKind = player -> !player.goalie() == wantsSkater;
            values.put(key, held.stream()
                    .filter(ofThisKind)
                    .mapToDouble(player -> contribution(scores, player, key) * shares.get(player.playerId()))
                    .sum());
            fullValues.put(key, held.stream()
                    .filter(ofThisKind)
                    .mapToDouble(player -> contribution(scores, player, key))
                    .sum());
        }
        double fullTotal = held.stream().mapToDouble(player -> value(scores, player)).sum();

        List<LeagueSummary.RosterRow> roster = held.stream()
                .map(player -> rosterRow(
                        player,
                        scores,
                        league.categoryKeys(),
                        shares.get(player.playerId()),
                        team.reserve().get(player.playerId()),
                        counted.contains(player.playerId())))
                .sorted(Comparator.comparingDouble(LeagueSummary.RosterRow::total).reversed())
                .toList();

        // The slot breakdown is the team's best lineup, one slot per player and the rest of those it
        // counts on the bench, each with all he counts for: night by night a centre-winger fills
        // whichever slot is free, but a reader looks for two left wings under LW, not a share of
        // five players (Alexander's call, 2026-10-10). A player the team does not count is in no
        // cell, so the bench holds as many as the league's bench does, and since he counts for
        // nothing the cells still sum to the total wherever the league has a bench.
        Map<Integer, Double> worth = new HashMap<>();
        held.forEach(player -> worth.put(player.playerId(), value(scores, player)));
        Map<Integer, LineupSlot> standing = NightlyLineups.standing(
                lineupPlayers.stream()
                        .sorted(Comparator.comparingDouble((NightlyLineups.Player player) -> worth.get(player.playerId()))
                                .reversed()
                                .thenComparingInt(NightlyLineups.Player::playerId))
                        .toList(),
                league.seats());
        Map<String, List<LeagueSummary.Contributor>> positionPlayers = new LinkedHashMap<>();
        for (String key : league.positionKeys()) {
            List<Ranked> ranked = new ArrayList<>();
            for (ScoredPlayer player : held) {
                LineupSlot slot = standing.get(player.playerId());
                boolean here = BENCH.equals(key)
                        ? slot == null && counted.contains(player.playerId())
                        : slot != null && slot.column().equals(key);
                if (here) {
                    ranked.add(new Ranked(
                            player.playerId(),
                            player.name(),
                            worth.get(player.playerId()) * shares.get(player.playerId())));
                }
            }
            List<LeagueSummary.Contributor> contributors = ranked.stream()
                    .sorted(Comparator.comparingDouble(Ranked::value).reversed()
                            .thenComparingInt(Ranked::playerId))
                    .map(rank -> new LeagueSummary.Contributor(rank.name(), rank.value()))
                    .toList();
            positionPlayers.put(key, contributors);
            values.put(key, contributors.stream().mapToDouble(LeagueSummary.Contributor::value).sum());
        }

        return new LeagueSummary.Team(
                team.teamId(), team.name(), team.mine(), total, values, fullTotal, fullValues, roster,
                positionPlayers);
    }

    /**
     * The players a team's lineup is picked from: its best by value, as many as the roster holds,
     * starters and bench. Whoever is cut goes worst first, but not a goalie the G slots need, nor a
     * skater the skater slots need: a weak backup goalie still starts more than a sixteenth skater.
     */
    static Set<Integer> counted(List<ScoredPlayer> held, ProjectionScoring.Scores scores, RosterSlots lineup) {
        Set<Integer> counted = new HashSet<>();
        held.forEach(player -> counted.add(player.playerId()));
        int excess = held.size() - (lineup.skaterSlots() + lineup.g());
        if (excess <= 0) {
            return counted;
        }
        long goalies = held.stream().filter(ScoredPlayer::goalie).count();
        long skaters = held.size() - goalies;
        int skaterSeats = lineup.skaterSlots() - lineup.bn();
        List<ScoredPlayer> worstFirst = held.stream()
                .sorted(Comparator.comparingDouble((ScoredPlayer player) -> value(scores, player))
                        .thenComparing(Comparator.comparingInt(ScoredPlayer::playerId).reversed()))
                .toList();
        for (ScoredPlayer player : worstFirst) {
            if (excess == 0) {
                break;
            }
            if (player.goalie() ? goalies <= lineup.g() : skaters <= skaterSeats) {
                continue;
            }
            counted.remove(player.playerId());
            excess--;
            if (player.goalie()) {
                goalies--;
            } else {
                skaters--;
            }
        }
        return counted;
    }

    /** A player under a slot, before the slot's list is put in order. */
    private record Ranked(int playerId, String name, double value) {
    }

    /**
     * Where each held goalie's share of his club's one nightly draw begins: a club's goalies,
     * whichever teams hold them, laid end to end, so two of them start the same game only where
     * their lines claim more starts than the club plays.
     */
    private static Map<Integer, Double> creaseOffsets(
            List<TeamPicks> teams, Map<Integer, ScoredPlayer> byId, LeagueSchedule schedule) {
        Map<String, TreeMap<Integer, ScoredPlayer>> byClub = new HashMap<>();
        for (TeamPicks team : teams) {
            for (Integer playerId : team.playerIds()) {
                ScoredPlayer player = byId.get(playerId);
                if (player != null && player.goalie() && player.team() != null) {
                    byClub.computeIfAbsent(player.team(), club -> new TreeMap<>()).put(playerId, player);
                }
            }
        }
        Map<Integer, Double> offsets = new HashMap<>();
        byClub.values().forEach(goalies -> {
            double offset = 0;
            for (ScoredPlayer goalie : goalies.values()) {
                offsets.put(goalie.playerId(), offset);
                offset = (offset + availability(goalie, schedule)) % 1;
            }
        });
        return offsets;
    }

    /**
     * The chance a player plays any one of his club's games: his line's games over the club's
     * season. A line that names no games, or a club the schedule does not know, is read as every
     * game — the schedule then decides whether he plays at all.
     */
    static double availability(ScoredPlayer player, LeagueSchedule schedule) {
        Integer clubGames = player.team() == null ? null : schedule.seasonGames().get(player.team());
        double games = player.gamesPlayed();
        if (clubGames == null || clubGames <= 0 || games <= 0) {
            return 1;
        }
        return Math.min(1, games / clubGames);
    }

    /** What one of his games is worth, which decides who starts on a night with no room for all. */
    private static double perGame(ScoredPlayer player, ProjectionScoring.Scores scores, LeagueSchedule schedule) {
        double games = player.gamesPlayed();
        if (games <= 0) {
            Integer clubGames = player.team() == null ? null : schedule.seasonGames().get(player.team());
            games = clubGames == null || clubGames <= 0 ? 1 : clubGames;
        }
        return value(scores, player) / games;
    }

    private static double value(ProjectionScoring.Scores scores, ScoredPlayer player) {
        return scores.values().getOrDefault(player.playerId(), 0.0);
    }

    /**
     * One player as a row under his team: his raw line, and what he brings the team — his value
     * and his share of each category cell, scaled to the games his lineup starts him in.
     *
     * @param share the share of his games he starts, 0 to 1
     * @param reserveSlot the injured-reserve or not-active slot he is parked in today, or null
     * @param counted whether he is among the players the team's lineup is picked from
     */
    static LeagueSummary.RosterRow rosterRow(
            ScoredPlayer player,
            ProjectionScoring.Scores scores,
            List<String> categoryKeys,
            double share,
            String reserveSlot,
            boolean counted) {
        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, Double> contributions = new LinkedHashMap<>();
        Map<String, Double> fullContributions = new LinkedHashMap<>();
        for (String key : categoryKeys) {
            boolean applies = ScoringStatKeys.SKATER_SCORING_SET.contains(key) == !player.goalie();
            values.put(key, applies ? Double.valueOf(player.scoringValue(key)) : null);
            contributions.put(key, applies ? Double.valueOf(contribution(scores, player, key) * share) : null);
            fullContributions.put(key, applies ? Double.valueOf(contribution(scores, player, key)) : null);
        }
        return new LeagueSummary.RosterRow(
                player.playerId(),
                player.name(),
                player.team(),
                positionsInOrder(player),
                value(scores, player) * share,
                value(scores, player),
                values,
                contributions,
                fullContributions,
                reserveSlot,
                counted);
    }

    /** The order a lineup is read in, since the pool's positions come as a set with none. */
    private static final List<String> POSITION_ORDER = List.of("C", "LW", "RW", "D", "G");

    private static List<String> positionsInOrder(ScoredPlayer player) {
        return POSITION_ORDER.stream().filter(player.positions()::contains).toList();
    }

    private static double contribution(ProjectionScoring.Scores scores, ScoredPlayer player, String key) {
        Double value = scores.contributions()
                .getOrDefault(player.playerId(), Map.of())
                .get(key);
        return value == null ? 0 : value;
    }
}
