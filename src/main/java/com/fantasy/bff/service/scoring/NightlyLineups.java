package com.fantasy.bff.service.scoring;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How much of what a team's players will do its lineup actually starts: night by night over the
 * schedule, the players whose club plays that night fill the league's slots best first, and the
 * rest sit on the bench and score nothing.
 *
 * <p>A team of forwards and nothing else is the case this exists for: on paper it out-scores
 * everyone, but its D and G slots stand empty every night and on a busy night half its forwards
 * are benched (Alexander's call, 2026-10-09, fantasy-bff#363).
 *
 * <p>Missed games are simulated rather than assumed away: a player plays any one of his club's
 * games with the chance his line's games played gives, so the bench fills in for an injured star
 * the way it does in a real season. A club's goalies share one draw per night, so two goalies of
 * the same club never both start one game unless their lines say more starts than the club has.
 * The draws are a fixed hash of the run, the player (or club) and the night, never a clock or a
 * shared random stream, so a league reads the same twice and one team's numbers do not move with
 * another's.
 *
 * <p>Filling the slots best first, letting a player already seated move to another slot he may
 * fill when that frees one for the next (an augmenting path), gives the best lineup there is, not
 * an approximation: the sets of players a lineup can seat at once form a matroid, on which greedy
 * by value is optimal.
 */
final class NightlyLineups {

    /** How many seasons are simulated. Each player plays dozens of games in each, so a hundred is plenty. */
    static final int RUNS = 100;

    private static final long SKATER_DRAW = 1;
    private static final long CREASE_DRAW = 2;

    /** The order seats are tried in: the named slots before the flex slots that could hold anyone. */
    private static final List<LineupSlot> SEATING_ORDER = Arrays.stream(LineupSlot.values())
            .sorted(Comparator.comparing(LineupSlot::flex).thenComparing(Enum::ordinal))
            .toList();

    private NightlyLineups() {
    }

    /**
     * One of a team's players, as the lineup needs him.
     *
     * @param club his club as the schedule spells it; null where the pool lists none, and then he
     *     never plays
     * @param perGame what one of his games is worth, which decides who starts when there is not
     *     room for everyone
     * @param availability the chance he plays any one of his club's games, 0 to 1
     * @param creaseOffset for a goalie, where his share of the club's nightly draw begins
     */
    record Player(
            int playerId,
            boolean goalie,
            Set<String> positions,
            String club,
            double perGame,
            double availability,
            double creaseOffset) {
    }

    /** The seats a league's lineup has, one per slot it counts, in the order they are tried. */
    static List<LineupSlot> seats(com.fantasy.bff.dto.response.RosterSlots slots) {
        List<LineupSlot> seats = new ArrayList<>();
        for (LineupSlot slot : SEATING_ORDER) {
            for (int index = 0; index < slot.count(slots); index++) {
                seats.add(slot);
            }
        }
        return seats;
    }

    /**
     * What each player starts, as a share of the games he plays, per slot he starts in. Summed over
     * the slots it is the share of his games that count; a player who never starts, or never plays
     * in the stretch, has no entry.
     */
    static Map<Integer, Map<LineupSlot, Double>> starts(
            List<Player> players, List<LineupSlot> seats, LeagueSchedule schedule) {
        List<LocalDate> nights = schedule.nights();
        Map<LocalDate, Integer> nightIndex = new HashMap<>();
        for (int index = 0; index < nights.size(); index++) {
            nightIndex.put(nights.get(index), index);
        }

        List<Player> ordered = players.stream()
                .sorted(Comparator.comparingDouble(Player::perGame).reversed()
                        .thenComparingInt(Player::playerId))
                .toList();
        int count = ordered.size();

        // Who could play each night, best first: everyone whose club has a game.
        List<List<Integer>> byNight = new ArrayList<>(nights.size());
        for (int night = 0; night < nights.size(); night++) {
            byNight.add(new ArrayList<>());
        }
        for (int player = 0; player < count; player++) {
            String club = ordered.get(player).club();
            List<LocalDate> dates = club == null ? List.of() : schedule.window().getOrDefault(club, List.of());
            for (LocalDate date : dates) {
                byNight.get(nightIndex.get(date)).add(player);
            }
        }

        boolean[][] eligible = new boolean[count][seats.size()];
        for (int player = 0; player < count; player++) {
            Player candidate = ordered.get(player);
            for (int seat = 0; seat < seats.size(); seat++) {
                eligible[player][seat] = seats.get(seat).eligible(candidate.goalie(), candidate.positions());
            }
        }

        long[] plays = new long[count];
        long[][] slotStarts = new long[count][LineupSlot.values().length];
        int[] owner = new int[seats.size()];
        boolean[] tried = new boolean[seats.size()];
        int[] playing = new int[count];

        for (int run = 0; run < RUNS; run++) {
            for (int night = 0; night < nights.size(); night++) {
                int tonight = 0;
                for (int player : byNight.get(night)) {
                    if (plays(ordered.get(player), run, night)) {
                        playing[tonight++] = player;
                        plays[player]++;
                    }
                }
                if (tonight == 0) {
                    continue;
                }
                Arrays.fill(owner, -1);
                for (int index = 0; index < tonight; index++) {
                    if (!takeFreeSeat(playing[index], eligible, owner)) {
                        Arrays.fill(tried, false);
                        seat(playing[index], eligible, owner, tried);
                    }
                }
                keepFlexForTheMarginal(seats, eligible, owner);
                for (int seat = 0; seat < seats.size(); seat++) {
                    if (owner[seat] >= 0) {
                        slotStarts[owner[seat]][seats.get(seat).ordinal()]++;
                    }
                }
            }
        }

        Map<Integer, Map<LineupSlot, Double>> shares = new HashMap<>();
        for (int player = 0; player < count; player++) {
            if (plays[player] == 0) {
                continue;
            }
            Map<LineupSlot, Double> bySlot = new EnumMap<>(LineupSlot.class);
            for (LineupSlot slot : LineupSlot.values()) {
                long started = slotStarts[player][slot.ordinal()];
                if (started > 0) {
                    bySlot.put(slot, (double) started / plays[player]);
                }
            }
            if (!bySlot.isEmpty()) {
                shares.put(ordered.get(player).playerId(), bySlot);
            }
        }
        return shares;
    }

    /**
     * Seats a player in the first empty seat he may fill, if there is one: tried before moving
     * anyone, so a better player keeps the named slot he took and the newcomer takes the flex.
     */
    private static boolean takeFreeSeat(int player, boolean[][] eligible, int[] owner) {
        for (int seat = 0; seat < owner.length; seat++) {
            if (owner[seat] < 0 && eligible[player][seat]) {
                owner[seat] = player;
                return true;
            }
        }
        return false;
    }

    /**
     * Who starts is settled; this only decides where, for the slot breakdown. A flex seat holding a
     * better player than a named seat he could fill swaps the two, so the flex keeps the marginal
     * starter, the way a manager reads a lineup. Players are numbered best first.
     */
    private static void keepFlexForTheMarginal(List<LineupSlot> seats, boolean[][] eligible, int[] owner) {
        boolean swapped = true;
        while (swapped) {
            swapped = false;
            for (int flex = 0; flex < owner.length && !swapped; flex++) {
                int flexPlayer = owner[flex];
                if (!seats.get(flex).flex() || flexPlayer < 0) {
                    continue;
                }
                for (int named = 0; named < owner.length; named++) {
                    int namedPlayer = owner[named];
                    if (seats.get(named).flex()
                            || namedPlayer < flexPlayer
                            || !eligible[flexPlayer][named]
                            || !eligible[namedPlayer][flex]) {
                        continue;
                    }
                    owner[named] = flexPlayer;
                    owner[flex] = namedPlayer;
                    swapped = true;
                    break;
                }
            }
        }
    }

    /** Seats a player, moving those already seated about if that makes room for him. */
    private static boolean seat(int player, boolean[][] eligible, int[] owner, boolean[] tried) {
        for (int seat = 0; seat < owner.length; seat++) {
            if (tried[seat] || !eligible[player][seat]) {
                continue;
            }
            tried[seat] = true;
            if (owner[seat] < 0 || seat(owner[seat], eligible, owner, tried)) {
                owner[seat] = player;
                return true;
            }
        }
        return false;
    }

    /** Whether a player plays one of his club's games in one simulated season. */
    private static boolean plays(Player player, int run, int night) {
        if (player.availability() >= 1) {
            return true;
        }
        if (player.availability() <= 0) {
            return false;
        }
        if (!player.goalie()) {
            return unit(SKATER_DRAW, run, player.playerId(), night) < player.availability();
        }
        // One draw per club and night, each of its goalies starting on his own stretch of it.
        double draw = unit(CREASE_DRAW, run, player.club().hashCode(), night) - player.creaseOffset();
        if (draw < 0) {
            draw += 1;
        }
        return draw < player.availability();
    }

    /** A number in [0, 1) fixed by its four keys. */
    private static double unit(long kind, long run, long id, long night) {
        long z = mix(kind);
        z = mix(z + run);
        z = mix(z + id);
        z = mix(z + night);
        return (z >>> 11) * 0x1.0p-53;
    }

    /** SplitMix64's finaliser. */
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
