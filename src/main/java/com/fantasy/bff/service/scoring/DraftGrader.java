package com.fantasy.bff.service.scoring;

import com.fantasy.bff.dto.response.DraftAnalysisAlternative;
import com.fantasy.bff.dto.response.DraftAnalysisPick;
import com.fantasy.bff.dto.response.DraftAnalysisTeam;
import com.fantasy.bff.dto.response.DraftPickGrade;
import com.fantasy.bff.dto.response.LeagueDraftPick;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sets a league's picks against the model's ranking of the whole pool.
 *
 * <p>A pick is graded by where the model ranked the player against where he went, with a round of
 * slack on both sides: {@code (pick + teams) / (rank + teams)}. The slack is what keeps the top of
 * a draft fair — the second-best player taken first is a fine pick, though it is half his rank — and
 * it fades as the numbers grow, so a player taken fifty places early in the tenth round still reads
 * as a reach. Taking the model's own player at every pick grades FAIR throughout.
 *
 * <p>What a pick gained is the player's value less the value of the model's player at that number
 * (the value over the slot), and a team's draft is the sum of its picks'.
 */
public final class DraftGrader {

    static final double STEAL_FROM = 1.5;
    static final double GOOD_FROM = 1.15;
    static final double FAIR_ABOVE = 0.87;
    static final double REACH_ABOVE = 0.67;

    private static final List<String> POSITION_ORDER = List.of("C", "LW", "RW", "D", "G");

    private DraftGrader() {
    }

    /** What a pick shows of a player, whether or not the model projects him. */
    public record Player(String name, String club, Set<String> positions) {
    }

    /** The graded picks in draft order, and the teams, best draft first. */
    public record Graded(List<DraftAnalysisPick> picks, List<DraftAnalysisTeam> teams) {
    }

    private record Ranked(int playerId, double value, int rank) {
    }

    /**
     * Grades a draft.
     *
     * @param values the model's value per player id under the league's scoring, for the whole pool
     * @param directory every player a pick may name, for his name and positions
     * @param picks the picks made, in order
     * @param teams the league's teams
     * @param leagueSize how many teams draft, which is the slack a grade allows
     * @param auction whether the draft was an auction, whose order says nothing about value: its
     *     picks are not graded
     */
    public static Graded grade(
            Map<Integer, Double> values,
            Map<Integer, Player> directory,
            List<LeagueDraftPick> picks,
            List<LeagueDraftTeam> teams,
            int leagueSize,
            boolean auction) {
        List<Ranked> ranked = ranked(values);
        Map<Integer, Ranked> byId = new HashMap<>();
        for (Ranked player : ranked) {
            byId.put(player.playerId(), player);
        }
        int slack = Math.max(1, leagueSize);

        Set<Integer> drafted = new HashSet<>();
        int next = 0;
        List<DraftAnalysisPick> graded = new ArrayList<>(picks.size());
        for (LeagueDraftPick pick : picks) {
            while (next < ranked.size() && drafted.contains(ranked.get(next).playerId())) {
                next++;
            }
            Ranked taken = byId.get(pick.playerId());
            Ranked best = next < ranked.size() ? ranked.get(next) : null;
            DraftAnalysisAlternative alternative =
                    best != null && best.playerId() != pick.playerId() && (taken == null || best.rank() < taken.rank())
                            ? alternative(best, directory.get(best.playerId()))
                            : null;
            drafted.add(pick.playerId());

            Player player = directory.get(pick.playerId());
            Double valueOverSlot = taken == null || ranked.isEmpty()
                    ? null
                    : taken.value() - ranked.get(Math.min(pick.overall(), ranked.size()) - 1).value();
            graded.add(new DraftAnalysisPick(
                    pick.overall(),
                    pick.round(),
                    pick.teamId(),
                    pick.playerId(),
                    player == null ? null : player.name(),
                    player == null ? null : player.club(),
                    player == null ? List.of() : positionsInOrder(player.positions()),
                    taken == null ? null : taken.rank(),
                    taken == null ? null : taken.value(),
                    valueOverSlot,
                    auction ? null : taken == null ? DraftPickGrade.UNRANKED : grade(pick.overall(), taken.rank(), slack),
                    alternative));
        }
        return new Graded(List.copyOf(graded), teams(teams, graded));
    }

    /** The grade of a pick at {@code overall} of a player the model ranks at {@code rank}. */
    static DraftPickGrade grade(int overall, int rank, int slack) {
        double ratio = (double) (overall + slack) / (rank + slack);
        if (ratio >= STEAL_FROM) {
            return DraftPickGrade.STEAL;
        }
        if (ratio >= GOOD_FROM) {
            return DraftPickGrade.GOOD;
        }
        if (ratio > FAIR_ABOVE) {
            return DraftPickGrade.FAIR;
        }
        if (ratio > REACH_ABOVE) {
            return DraftPickGrade.REACH;
        }
        return DraftPickGrade.BIG_REACH;
    }

    /** The pool by value, best first; a tie goes to the lower id, so the order is the same every time. */
    private static List<Ranked> ranked(Map<Integer, Double> values) {
        List<Map.Entry<Integer, Double>> entries = values.entrySet().stream()
                .filter(entry -> entry.getValue() != null && Double.isFinite(entry.getValue()))
                .sorted(Comparator.comparingDouble((Map.Entry<Integer, Double> entry) -> entry.getValue())
                        .reversed()
                        .thenComparingInt(Map.Entry::getKey))
                .toList();
        List<Ranked> ranked = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            ranked.add(new Ranked(entries.get(index).getKey(), entries.get(index).getValue(), index + 1));
        }
        return ranked;
    }

    private static DraftAnalysisAlternative alternative(Ranked best, Player player) {
        if (player == null) {
            return null;
        }
        return new DraftAnalysisAlternative(
                best.playerId(), player.name(), player.club(), positionsInOrder(player.positions()), best.rank());
    }

    private static List<String> positionsInOrder(Set<String> positions) {
        return POSITION_ORDER.stream().filter(positions::contains).toList();
    }

    private static List<DraftAnalysisTeam> teams(List<LeagueDraftTeam> teams, List<DraftAnalysisPick> picks) {
        Map<String, List<DraftAnalysisPick>> byTeam = new LinkedHashMap<>();
        for (LeagueDraftTeam team : teams) {
            byTeam.put(team.id(), new ArrayList<>());
        }
        for (DraftAnalysisPick pick : picks) {
            List<DraftAnalysisPick> made = byTeam.get(pick.teamId());
            if (made != null) {
                made.add(pick);
            }
        }
        return teams.stream()
                .map(team -> team(team, byTeam.get(team.id())))
                .sorted(Comparator.comparingDouble(DraftAnalysisTeam::valueAdded).reversed())
                .toList();
    }

    private static DraftAnalysisTeam team(LeagueDraftTeam team, List<DraftAnalysisPick> picks) {
        double valueAdded = 0;
        int good = 0;
        int bad = 0;
        int graded = 0;
        int points = 0;
        for (DraftAnalysisPick pick : picks) {
            if (pick.valueOverSlot() != null) {
                valueAdded += pick.valueOverSlot();
            }
            DraftPickGrade grade = pick.grade();
            if (grade == null || grade == DraftPickGrade.UNRANKED) {
                continue;
            }
            graded++;
            points += points(grade);
            if (grade == DraftPickGrade.STEAL || grade == DraftPickGrade.GOOD) {
                good++;
            } else if (grade == DraftPickGrade.REACH || grade == DraftPickGrade.BIG_REACH) {
                bad++;
            }
        }
        return new DraftAnalysisTeam(
                team.id(),
                team.name(),
                team.mine(),
                picks.size(),
                valueAdded,
                good,
                bad,
                graded == 0 ? null : letter((double) points / graded));
    }

    private static int points(DraftPickGrade grade) {
        return switch (grade) {
            case STEAL -> 2;
            case GOOD -> 1;
            case REACH -> -1;
            case BIG_REACH -> -2;
            default -> 0;
        };
    }

    /** A team's letter from its picks' mean grade, where STEAL is +2 and BIG_REACH -2. */
    static String letter(double meanGrade) {
        if (meanGrade >= 0.6) {
            return "A";
        }
        if (meanGrade >= 0.2) {
            return "B";
        }
        if (meanGrade > -0.2) {
            return "C";
        }
        if (meanGrade > -0.6) {
            return "D";
        }
        return "F";
    }
}
