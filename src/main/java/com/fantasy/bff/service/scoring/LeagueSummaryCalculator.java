package com.fantasy.bff.service.scoring;

import com.fantasy.bff.dto.response.RosterSlots;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Adds a drafted league up: every team's best players, as many as the league plays, placed into its
 * lineup, and its totals by category and by slot.
 *
 * <p>A port of {@code fantasy-web/src/app/draft-mode/league-projection.ts}, minus the column
 * labels and decimals — how a number is written is the web's business, and keeping the copy on one
 * side means a renamed column is a web change and nothing else.
 */
@Component
public class LeagueSummaryCalculator {

    private static final String BENCH = "BN";

    /** The lineup's named slots, in the order a manager reads them, then the flex. */
    private static final List<Slot> STARTING_SLOTS = List.of(
            new Slot("lw", "LW"),
            new Slot("c", "C"),
            new Slot("rw", "RW"),
            new Slot("d", "D"),
            new Slot("util", "UTIL"),
            new Slot("g", "G"));

    private record Slot(String key, String column) {

        int count(RosterSlots slots) {
            return switch (key) {
                case "c" -> slots.c();
                case "lw" -> slots.lw();
                case "rw" -> slots.rw();
                case "d" -> slots.d();
                case "util" -> slots.util();
                case "g" -> slots.g();
                default -> 0;
            };
        }

        boolean eligible(Placed player) {
            return switch (key) {
                case "c" -> !player.goalie() && player.positions().contains("C");
                case "lw" -> !player.goalie() && player.positions().contains("LW");
                case "rw" -> !player.goalie() && player.positions().contains("RW");
                case "d" -> !player.goalie() && player.positions().contains("D");
                case "util" -> !player.goalie();
                case "g" -> player.goalie();
                default -> false;
            };
        }
    }

    /** One team as it was drafted: who it is, and the players it took, in pick order. */
    public record TeamPicks(String teamId, String name, boolean mine, List<Integer> playerIds) {
    }

    /**
     * Totals a drafted league.
     *
     * @param pool every row the ranking is computed over, drafted or not — a category z-score is
     *     measured against the whole pool, so a summary of the drafted players alone would score
     *     them against each other and say nothing
     * @param teams the teams and their picks
     * @param league how the league scores
     * @return the teams, best total first, with the per-player halves filled in; drop them with
     *     {@link LeagueSummary#aggregatesOnly()} for an account that may not see them
     */
    public LeagueSummary summarise(List<ScoredPlayer> pool, List<TeamPicks> teams, LeagueScoring league) {
        ProjectionScoring.Scores scores = ProjectionScoring.score(pool, league);
        Map<Integer, ScoredPlayer> byId = new HashMap<>();
        for (ScoredPlayer player : pool) {
            byId.putIfAbsent(player.playerId(), player);
        }

        // The league's own order, which is the order its columns are read in.
        List<String> categoryKeys = league.activeScoringColumns();

        List<Partial> partials = teams.stream()
                .map(team -> partial(team, byId, scores, categoryKeys, league))
                .toList();

        boolean includeBench = league.rosterSlots().bn() > 0
                || partials.stream().anyMatch(partial -> !partial.bench().isEmpty());
        List<String> positionKeys = positionKeys(league.rosterSlots(), includeBench);

        List<LeagueSummary.Team> rows = partials.stream()
                .map(partial -> row(partial, positionKeys))
                .sorted(Comparator.comparingDouble(LeagueSummary.Team::total).reversed())
                .toList();

        return new LeagueSummary(categoryKeys, positionKeys, rows);
    }

    private record Placed(int playerId, String name, double score, boolean goalie, java.util.Set<String> positions) {
    }

    private record Partial(
            String teamId,
            String name,
            boolean mine,
            double total,
            Map<String, Double> values,
            List<LeagueSummary.RosterRow> roster,
            Map<String, List<Placed>> byColumn,
            List<Placed> bench) {
    }

    private Partial partial(
            TeamPicks team,
            Map<Integer, ScoredPlayer> byId,
            ProjectionScoring.Scores scores,
            List<String> categoryKeys,
            LeagueScoring league) {
        List<ScoredPlayer> drafted = counted(team.playerIds().stream()
                .map(byId::get)
                .filter(player -> player != null)
                .toList(), scores, league.countedPlayers());

        double total = drafted.stream()
                .mapToDouble(player -> scores.values().getOrDefault(player.playerId(), 0.0))
                .sum();

        // A category cell aggregates only the players the stat applies to — a goalie contributes
        // no hits, a skater no saves — so each column sums over its own side of the roster.
        Map<String, Double> values = new LinkedHashMap<>();
        for (String key : categoryKeys) {
            boolean wantsSkater = ScoringStatKeys.SKATER_SCORING_SET.contains(key);
            Predicate<ScoredPlayer> ofThisKind = player -> !player.goalie() == wantsSkater;
            values.put(key, drafted.stream()
                    .filter(ofThisKind)
                    .mapToDouble(player -> contribution(scores, player, key))
                    .sum());
        }

        List<LeagueSummary.RosterRow> roster = drafted.stream()
                .map(player -> rosterRow(player, scores, categoryKeys))
                .sorted(Comparator.comparingDouble(LeagueSummary.RosterRow::total).reversed())
                .toList();

        List<Placed> placeable = drafted.stream()
                .map(player -> new Placed(
                        player.playerId(),
                        player.name(),
                        scores.values().getOrDefault(player.playerId(), 0.0),
                        player.goalie(),
                        player.positions()))
                .toList();
        Lineup lineup = assign(placeable, league.rosterSlots());

        return new Partial(
                team.teamId(), team.name(), team.mine(), total, values, roster,
                lineup.byColumn(), lineup.bench());
    }

    /**
     * A team's best players by value, as many as the league counts, whatever slot each holds in
     * the league: an injured star the projection still rates above his replacement counts, and
     * the replacement drops out. Picked by value alone, not by who fits the lineup — placing them
     * is {@link #assign}'s job. The kept players stay in the order they came in, so the sums add up
     * in the same order as before.
     */
    private List<ScoredPlayer> counted(List<ScoredPlayer> players, ProjectionScoring.Scores scores, int limit) {
        if (players.size() <= limit) {
            return players;
        }
        Set<Integer> best = players.stream()
                .sorted(Comparator.comparingDouble(
                                (ScoredPlayer player) -> scores.values().getOrDefault(player.playerId(), 0.0))
                        .reversed()
                        .thenComparingInt(ScoredPlayer::playerId))
                .limit(limit)
                .map(ScoredPlayer::playerId)
                .collect(Collectors.toSet());
        return players.stream().filter(player -> best.contains(player.playerId())).toList();
    }

    private LeagueSummary.RosterRow rosterRow(
            ScoredPlayer player, ProjectionScoring.Scores scores, List<String> categoryKeys) {
        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, Double> contributions = new LinkedHashMap<>();
        for (String key : categoryKeys) {
            boolean applies = ScoringStatKeys.SKATER_SCORING_SET.contains(key) == !player.goalie();
            values.put(key, applies ? Double.valueOf(player.scoringValue(key)) : null);
            contributions.put(key, applies ? Double.valueOf(contribution(scores, player, key)) : null);
        }
        return new LeagueSummary.RosterRow(
                player.playerId(),
                player.name(),
                scores.values().getOrDefault(player.playerId(), 0.0),
                values,
                contributions);
    }

    private double contribution(ProjectionScoring.Scores scores, ScoredPlayer player, String key) {
        Double value = scores.contributions()
                .getOrDefault(player.playerId(), Map.of())
                .get(key);
        return value == null ? 0 : value;
    }

    private record Lineup(Map<String, List<Placed>> byColumn, List<Placed> bench) {
    }

    /**
     * Places a team's drafted players into its lineup so each counts once and the weakest end up
     * on the bench.
     *
     * <p>Two phases. Players are matched best-first to the named slots (C/LW/RW/D/G) as a maximum
     * matching with augmenting reassignment — so a dual-position player yields a named slot to a
     * single-position player when that lets more of the roster start. Whoever is left fills the
     * Util flex best-first (skaters only); the rest fall to the bench. Filling Util from the
     * leftovers keeps the strongest players in their named slots and leaves the marginal starter
     * in Util, which is how a manager reads a lineup.
     */
    private Lineup assign(List<Placed> players, RosterSlots slots) {
        List<Slot> namedSlots = new ArrayList<>();
        for (Slot slot : STARTING_SLOTS) {
            if ("util".equals(slot.key())) {
                continue;
            }
            for (int index = 0; index < slot.count(slots); index++) {
                namedSlots.add(slot);
            }
        }

        List<Placed> ordered = players.stream()
                .sorted(Comparator.comparingDouble(Placed::score).reversed()
                        .thenComparingInt(Placed::playerId))
                .toList();
        Integer[] slotToPlayer = new Integer[namedSlots.size()];
        Integer[] playerToSlot = new Integer[ordered.size()];

        for (int index = 0; index < ordered.size(); index++) {
            tryAssign(index, new boolean[namedSlots.size()], namedSlots, ordered, slotToPlayer, playerToSlot);
        }

        Map<String, List<Placed>> byColumn = new LinkedHashMap<>();
        List<Placed> leftover = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            Integer slot = playerToSlot[index];
            if (slot == null) {
                leftover.add(ordered.get(index));
                continue;
            }
            byColumn.computeIfAbsent(namedSlots.get(slot).column(), key -> new ArrayList<>())
                    .add(ordered.get(index));
        }

        // Leftovers stay in descending-score order; the best eligible skaters take the flex.
        List<Placed> util = new ArrayList<>();
        List<Placed> bench = new ArrayList<>();
        for (Placed player : leftover) {
            if (!player.goalie() && util.size() < slots.util()) {
                util.add(player);
            } else {
                bench.add(player);
            }
        }
        if (!util.isEmpty()) {
            byColumn.put("UTIL", util);
        }
        return new Lineup(byColumn, bench);
    }

    private boolean tryAssign(
            int playerIndex,
            boolean[] visited,
            List<Slot> namedSlots,
            List<Placed> ordered,
            Integer[] slotToPlayer,
            Integer[] playerToSlot) {
        for (int slot = 0; slot < namedSlots.size(); slot++) {
            if (visited[slot] || !namedSlots.get(slot).eligible(ordered.get(playerIndex))) {
                continue;
            }
            visited[slot] = true;
            Integer occupant = slotToPlayer[slot];
            if (occupant == null
                    || tryAssign(occupant, visited, namedSlots, ordered, slotToPlayer, playerToSlot)) {
                slotToPlayer[slot] = playerIndex;
                playerToSlot[playerIndex] = slot;
                return true;
            }
        }
        return false;
    }

    private List<String> positionKeys(RosterSlots slots, boolean includeBench) {
        List<String> keys = new ArrayList<>(STARTING_SLOTS.stream()
                .filter(slot -> slot.count(slots) > 0)
                .map(Slot::column)
                .toList());
        if (includeBench) {
            keys.add(BENCH);
        }
        return List.copyOf(keys);
    }

    private LeagueSummary.Team row(Partial partial, List<String> positionKeys) {
        Map<String, Double> values = new LinkedHashMap<>(partial.values());
        Map<String, List<LeagueSummary.Contributor>> positionPlayers = new LinkedHashMap<>();
        for (String key : positionKeys) {
            List<Placed> assigned = BENCH.equals(key)
                    ? partial.bench()
                    : partial.byColumn().getOrDefault(key, List.of());
            List<LeagueSummary.Contributor> contributors = assigned.stream()
                    .sorted(Comparator.comparingDouble(Placed::score).reversed()
                            .thenComparingInt(Placed::playerId))
                    .map(player -> new LeagueSummary.Contributor(player.name(), player.score()))
                    .toList();
            positionPlayers.put(key, contributors);
            values.put(key, contributors.stream()
                    .mapToDouble(LeagueSummary.Contributor::value)
                    .sum());
        }
        return new LeagueSummary.Team(
                partial.teamId(),
                partial.name(),
                partial.mine(),
                partial.total(),
                values,
                partial.roster(),
                positionPlayers);
    }
}
