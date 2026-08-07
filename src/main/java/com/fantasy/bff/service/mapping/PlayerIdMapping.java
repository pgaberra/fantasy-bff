package com.fantasy.bff.service.mapping;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The outcome of matching NHL players to a fantasy platform's players.
 *
 * <p>Unmatched players are carried deliberately rather than dropped: some of them should never
 * match — a platform only lists players with fantasy relevance, so fringe and long-term absent
 * players correctly have no counterpart — and the rest are how a broken match is noticed. A
 * resolver that silently returned fewer rows would look identical either way.
 */
public record PlayerIdMapping(
        Map<Long, Integer> nhlIdToPlatformId,
        List<Unmatched> unmatched,
        int matchedOnName,
        int matchedOnFallback,
        int matchedOnOverride) {

    /** An NHL player with no counterpart on the platform, and why. */
    public record Unmatched(long nhlId, String name, String team, Reason reason) {
        public enum Reason {
            /** Nothing on the platform carries this name in either form. */
            NOT_ON_PLATFORM,
            /** More than one platform player matched and nothing separated them. */
            AMBIGUOUS
        }
    }

    public Optional<Integer> platformId(long nhlId) {
        return Optional.ofNullable(nhlIdToPlatformId.get(nhlId));
    }

    public int matched() {
        return nhlIdToPlatformId.size();
    }

    public int total() {
        return matched() + unmatched.size();
    }

    /** Share of NHL players that found a counterpart, for logging and for the admin view. */
    public double coverage() {
        return total() == 0 ? 0.0 : (double) matched() / total();
    }
}
