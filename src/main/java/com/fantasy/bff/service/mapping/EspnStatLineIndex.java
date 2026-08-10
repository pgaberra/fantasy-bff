package com.fantasy.bff.service.mapping;

import com.fantasy.bff.generated.espn.model.PlayerStatLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Looks up a player's ESPN stat line from their Yahoo-side name and position.
 *
 * <p>Nobody publishes a Yahoo id → ESPN id crosswalk, so the two sides are matched on identity,
 * the same way {@link PlayerIdResolver} matches NHL players to a platform. The keys here differ
 * because the inputs do: both sides carry a single position, and neither team nor sweater
 * number is available on the ESPN stat line.
 *
 * <p>Measured against staging (1037 players who played last season): normalised full name plus
 * position matched 83.6%, full name alone recovered a further 14.6%, and last name plus first
 * initial recovered 1.1% more — familiar forms such as Yahoo's <i>Zachary</i> Bolduc for Zack.
 * Position is tried first rather than last because it is what separates two players who share a
 * name (the Sebastian Ahos); falling back to the name alone is safe because espn-service has
 * already dropped ESPN's duplicate records for the same person.
 */
public final class EspnStatLineIndex {

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
     * @param name the player's display name as Yahoo spells it
     * @param position the player's primary position (C, LW, RW, D or G)
     */
    public Optional<PlayerStatLine> find(String name, String position) {
        PlayerNameKey key = PlayerNameKey.of(name);
        return unique(byNameAndPosition, withPosition(key.fullName(), position))
                .or(() -> unique(byName, key.fullName()))
                .or(() -> key.hasFallback()
                        ? unique(byFallbackAndPosition, withPosition(key.lastNameInitial(), position))
                        : Optional.empty())
                .or(() -> key.hasFallback() ? unique(byFallback, key.lastNameInitial()) : Optional.empty());
    }

    private static Optional<PlayerStatLine> unique(Map<String, List<PlayerStatLine>> index, String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        List<PlayerStatLine> candidates = index.get(key);
        // More than one match means the name doesn't identify a person; guessing would attach
        // another player's stats.
        return candidates != null && candidates.size() == 1
                ? Optional.of(candidates.getFirst())
                : Optional.empty();
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
