package com.fantasy.bff.service.mapping;

import com.fantasy.bff.generated.espn.model.PlayerStatLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Matches players to their ESPN stat line.
 *
 * <p>Nobody publishes a Yahoo id → ESPN id crosswalk, so the two sides are matched on identity,
 * the same way {@link PlayerIdResolver} matches NHL players to a platform. The keys here differ
 * because the inputs do: both sides carry a single position, and neither team nor sweater
 * number is available on the ESPN stat line.
 *
 * <p>Measured against staging (1037 players who played last season): normalised full name plus
 * position matched 83.6%, full name alone recovered a further 14.6%, and last name plus first
 * initial recovered 1.1% more — familiar forms such as Yahoo's <i>Zachary</i> Bolduc for ESPN's
 * Zack. Position is tried first because it is what separates two players who share a name (the
 * Sebastian Ahos); falling back to the name alone is safe because espn-service has already
 * dropped ESPN's duplicate records for the same person.
 *
 * <p>The whole squad is matched at once rather than a player at a time, because <b>a stat line
 * belongs to one person</b>. Matching individually let Tyce Thompson — who ESPN doesn't carry —
 * fall through to the familiar-name form and collect Tage Thompson's season: a hat trick and
 * 1856 shifts, for a player who barely played. A line two people want is given to neither.
 */
public final class EspnStatLineIndex {

    /**
     * A player to match: the id the result is keyed by, plus what identifies them. The jersey
     * is what separates two people who share a name, so it may be absent but is never a filter.
     */
    public record Subject(int playerId, String name, String position, Integer sweaterNumber) {}

    private final Map<String, List<PlayerStatLine>> byNameAndPosition;
    private final Map<String, List<PlayerStatLine>> byName;
    private final Map<String, List<PlayerStatLine>> byFallbackAndPosition;
    private final Map<String, List<PlayerStatLine>> byFallback;

    public EspnStatLineIndex(List<PlayerStatLine> statLines) {
        this.byNameAndPosition = index(statLines, line -> withPosition(fullNameKey(line), line.getPosition()));
        this.byName = index(statLines, EspnStatLineIndex::fullNameKey);
        this.byFallbackAndPosition = index(statLines, line -> withPosition(fallbackKey(line), line.getPosition()));
        this.byFallback = index(statLines, EspnStatLineIndex::fallbackKey);
    }

    /** Matches nobody — what the BFF falls back to when espn-service can't be reached. */
    public static EspnStatLineIndex empty() {
        return new EspnStatLineIndex(List.of());
    }

    /**
     * Resolves every subject at once, keyed by {@link Subject#playerId()}. Players absent from
     * the result had no stat line of their own.
     */
    public Map<Integer, PlayerStatLine> matchAll(List<Subject> subjects) {
        Map<Integer, PlayerStatLine> matched = new LinkedHashMap<>();
        Set<Long> claimed = new HashSet<>();

        // The exact forms first, so a player ESPN carries under their own name always beats
        // someone else reaching the same line through a familiar-name guess.
        List<Subject> unmatched = assign(subjects, this::byExactName, matched, claimed);
        assign(unmatched, this::byFamiliarName, matched, claimed);
        return matched;
    }

    /**
     * Assigns what this lookup resolves unambiguously, and returns the subjects still without a
     * line. A line more than one subject resolves to identifies neither of them, so it goes to
     * nobody and stays available to no one.
     */
    private List<Subject> assign(List<Subject> subjects,
                                 Function<Subject, Optional<PlayerStatLine>> lookup,
                                 Map<Integer, PlayerStatLine> matched,
                                 Set<Long> claimed) {
        Map<Long, List<Subject>> claimants = new LinkedHashMap<>();
        Map<Long, PlayerStatLine> lines = new HashMap<>();
        List<Subject> unmatched = new ArrayList<>();

        for (Subject subject : subjects) {
            Optional<PlayerStatLine> line = lookup.apply(subject);
            if (line.isEmpty() || claimed.contains(line.get().getId())) {
                unmatched.add(subject);
                continue;
            }
            long id = line.get().getId();
            lines.put(id, line.get());
            claimants.computeIfAbsent(id, ignored -> new ArrayList<>()).add(subject);
        }

        for (Map.Entry<Long, List<Subject>> entry : claimants.entrySet()) {
            List<Subject> contenders = entry.getValue();
            PlayerStatLine line = lines.get(entry.getKey());
            claimed.add(entry.getKey());
            Subject owner = contenders.size() == 1 ? contenders.getFirst() : wearingTheJersey(contenders, line);
            for (Subject contender : contenders) {
                if (contender.equals(owner)) {
                    matched.put(contender.playerId(), line);
                } else {
                    unmatched.add(contender);
                }
            }
        }
        return unmatched;
    }

    /**
     * Which of several same-named players a line belongs to. Both Vancouver Petterssons answer
     * to the name; only one of them wears 40. Returns null when the jersey doesn't decide, and
     * then the line goes to nobody.
     */
    private static Subject wearingTheJersey(List<Subject> contenders, PlayerStatLine line) {
        if (line.getSweaterNumber() == null) {
            return null;
        }
        List<Subject> wearers = contenders.stream()
                .filter(subject -> line.getSweaterNumber().equals(subject.sweaterNumber()))
                .toList();
        return wearers.size() == 1 ? wearers.getFirst() : null;
    }

    private Optional<PlayerStatLine> byExactName(Subject subject) {
        PlayerNameKey key = PlayerNameKey.of(subject.name());
        return decide(byNameAndPosition, withPosition(key.fullName(), subject.position()), subject)
                .or(() -> decide(byName, key.fullName(), subject));
    }

    private Optional<PlayerStatLine> byFamiliarName(Subject subject) {
        PlayerNameKey key = PlayerNameKey.of(subject.name());
        if (!key.hasFallback()) {
            return Optional.empty();
        }
        return decide(byFallbackAndPosition, withPosition(key.lastNameInitial(), subject.position()), subject)
                .or(() -> decide(byFallback, key.lastNameInitial(), subject));
    }

    /**
     * The one line this player's name resolves to. Where the name alone reaches several — ESPN
     * carries two Matt Murrays in goal — the jersey decides; where it still doesn't, nothing is
     * returned, because guessing would attach another player's stats.
     */
    private static Optional<PlayerStatLine> decide(
            Map<String, List<PlayerStatLine>> index, String key, Subject subject) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        List<PlayerStatLine> candidates = index.get(key);
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.getFirst());
        }
        if (subject.sweaterNumber() == null) {
            return Optional.empty();
        }
        List<PlayerStatLine> wearingIt = candidates.stream()
                .filter(candidate -> subject.sweaterNumber().equals(candidate.getSweaterNumber()))
                .toList();
        return wearingIt.size() == 1 ? Optional.of(wearingIt.getFirst()) : Optional.empty();
    }

    private static String fullNameKey(PlayerStatLine line) {
        return PlayerNameKey.of(line.getFullName()).fullName();
    }

    private static String fallbackKey(PlayerStatLine line) {
        PlayerNameKey key = PlayerNameKey.of(line.getFullName());
        return key.hasFallback() ? key.lastNameInitial() : null;
    }

    private static String withPosition(String key, String position) {
        if (key == null || key.isEmpty() || position == null || position.isBlank()) {
            return null;
        }
        return key + "#" + position.toUpperCase(Locale.ROOT);
    }

    private static Map<String, List<PlayerStatLine>> index(
            List<PlayerStatLine> statLines, Function<PlayerStatLine, String> keyOf) {
        Map<String, List<PlayerStatLine>> index = new HashMap<>();
        for (PlayerStatLine line : statLines) {
            String key = keyOf.apply(line);
            if (key == null || key.isEmpty()) {
                continue;
            }
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(line);
        }
        return index;
    }
}
