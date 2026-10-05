package com.fantasy.bff.service.scoring;

import com.fantasy.bff.dto.response.RosterSlots;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a player is worth to a league that has to start a lineup, rather than in points alone: his
 * value over the best player at his position that nobody in the league would start.
 *
 * <p>A league that starts four defencemen and two goalies a team cannot draft only the forwards who
 * score the most: it has to fill those slots, and the tenth-best defenceman is a starter whose
 * replacement off the wire is far weaker than a forward's. Ranking on value over replacement is what
 * puts the best defenceman near the top of the draft where the market takes him, and a centre in a
 * league deep in centres further down.
 *
 * <p>The replacement level is read off the league itself. Every starting slot in the league (the
 * slots a team starts, times the teams) is filled from the pool best value first: a player takes a
 * slot at one of his own positions while there is one, the one with the most room left, and
 * otherwise a flex slot he fits, the wing flex before the forward flex before Util. Once every slot
 * is taken, a position's replacement level is the value of the best player eligible there who did
 * not get one. A player who plays several positions is measured at the one whose replacement is
 * weakest, which is where he is worth the most. The bench is left out: it is filled from the same
 * leftovers, and the replacement level is what the starting lineup is measured against.
 */
public final class ReplacementLevel {

    private static final List<String> POSITIONS = List.of("C", "LW", "RW", "D", "G");

    private ReplacementLevel() {
    }

    /**
     * One player, valued for a league.
     *
     * @param value the model's value for him under the league's scoring
     * @param overReplacement that value less his position's replacement level
     * @param positionRank where he ranks by value among the players at the position he is measured
     *     at, as the position and the number: {@code "D1"}
     */
    public record Valued(double value, double overReplacement, String positionRank) {
    }

    /**
     * Values a pool for a league.
     *
     * @param values the model's value per player id under the league's scoring
     * @param positions each player's eligible positions, C/LW/RW/D for a skater and G for a goalie; a
     *     player with none is left out
     * @param slots what one team starts
     * @param teams how many teams the league has
     * @return each player's value over his replacement, by player id
     */
    public static Map<Integer, Valued> value(
            Map<Integer, Double> values, Map<Integer, Set<String>> positions, RosterSlots slots, int teams) {
        List<Integer> ranked = values.entrySet().stream()
                .filter(entry -> entry.getValue() != null && Double.isFinite(entry.getValue()))
                .filter(entry -> !eligible(positions.get(entry.getKey())).isEmpty())
                .sorted(Comparator.comparingDouble((Map.Entry<Integer, Double> entry) -> entry.getValue())
                        .reversed()
                        .thenComparingInt(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();

        Map<String, Double> replacement = replacementLevels(ranked, values, positions, slots, Math.max(1, teams));

        Map<String, Integer> seenAt = new HashMap<>();
        Map<Integer, Integer> rankAt = new HashMap<>();
        Map<Integer, String> measuredAt = new HashMap<>();
        Map<Integer, Valued> valued = new LinkedHashMap<>();
        for (int playerId : ranked) {
            List<String> eligible = eligible(positions.get(playerId));
            String position = eligible.stream()
                    .min(Comparator.comparingDouble((String candidate) -> replacement.get(candidate))
                            .thenComparingInt(POSITIONS::indexOf))
                    .orElseThrow();
            measuredAt.put(playerId, position);
            for (String at : eligible) {
                int rank = seenAt.merge(at, 1, Integer::sum);
                if (at.equals(position)) {
                    rankAt.put(playerId, rank);
                }
            }
        }
        for (int playerId : ranked) {
            String position = measuredAt.get(playerId);
            double value = values.get(playerId);
            valued.put(playerId, new Valued(
                    value, value - replacement.get(position), position + rankAt.get(playerId)));
        }
        return valued;
    }

    /**
     * Each position's replacement level: the value of the best player eligible there who gets no
     * starting slot once the league's slots are filled. Where every player eligible at a position
     * starts, the weakest of them is the level, and nought where the pool has none at all.
     */
    static Map<String, Double> replacementLevels(
            List<Integer> ranked,
            Map<Integer, Double> values,
            Map<Integer, Set<String>> positions,
            RosterSlots slots,
            int teams) {
        Map<String, Integer> room = new HashMap<>();
        room.put("C", slots.c() * teams);
        room.put("LW", slots.lw() * teams);
        room.put("RW", slots.rw() * teams);
        room.put("D", slots.d() * teams);
        room.put("G", slots.g() * teams);
        room.put("W", slots.w() * teams);
        room.put("F", slots.f() * teams);
        room.put("UTIL", slots.util() * teams);

        Map<String, Double> best = new HashMap<>();
        Map<String, Double> weakest = new HashMap<>();
        for (int playerId : ranked) {
            List<String> eligible = eligible(positions.get(playerId));
            double value = values.get(playerId);
            for (String at : eligible) {
                weakest.put(at, value);
            }
            if (!start(eligible, room)) {
                for (String at : eligible) {
                    best.putIfAbsent(at, value);
                }
            }
        }
        Map<String, Double> levels = new HashMap<>();
        for (String position : POSITIONS) {
            levels.put(position, best.getOrDefault(position, weakest.getOrDefault(position, 0.0)));
        }
        return levels;
    }

    /** Puts a player in a starting slot if one is left for him; false where none is. */
    private static boolean start(List<String> eligible, Map<String, Integer> room) {
        String own = eligible.stream()
                .filter(position -> room.get(position) > 0)
                .max(Comparator.comparingInt((String position) -> room.get(position))
                        .thenComparing(position -> -POSITIONS.indexOf(position)))
                .orElse(null);
        if (own != null) {
            room.merge(own, -1, Integer::sum);
            return true;
        }
        boolean goalie = eligible.contains("G");
        boolean wing = eligible.contains("LW") || eligible.contains("RW");
        boolean forward = wing || eligible.contains("C");
        for (String flex : List.of("W", "F", "UTIL")) {
            boolean fits = switch (flex) {
                case "W" -> wing;
                case "F" -> forward;
                default -> !goalie;
            };
            if (fits && room.get(flex) > 0) {
                room.merge(flex, -1, Integer::sum);
                return true;
            }
        }
        return false;
    }

    private static List<String> eligible(Set<String> positions) {
        if (positions == null) {
            return List.of();
        }
        return POSITIONS.stream().filter(positions::contains).toList();
    }
}
