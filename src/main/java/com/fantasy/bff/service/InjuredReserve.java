package com.fantasy.bff.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A league's injured-reserve slots, and which players may sit in them. A slot there holds a player
 * without taking a roster spot, so an injured player moved into a free one makes room for a pickup
 * with nobody dropped.
 *
 * <p>The two platforms decide eligibility differently. Yahoo says it per player: an IR-eligible
 * player carries the slot among his eligible positions (on staging 80 of 1418 skaters carried
 * {@code IR}). ESPN lists its IR slot among every player's eligible slots, so there the injury
 * status decides: out, or on injured reserve.
 */
public final class InjuredReserve {

    /** The platforms' injured-reserve slot codes. Not-active (NA) is for prospects, not injuries. */
    public static final Set<String> SLOTS = Set.of("IR", "IR+", "IR-LT", "IR-NR");

    /** ESPN's injury statuses its IR slot takes. */
    private static final Set<String> ESPN_ELIGIBLE = Set.of("OUT", "INJURY_RESERVE");

    private InjuredReserve() {}

    /** Whether a roster code, in either platform's spelling, is an injured-reserve slot. */
    public static boolean isSlot(String code) {
        return code != null && SLOTS.contains(code.toUpperCase(Locale.ROOT));
    }

    /** The injured-reserve slots Yahoo lists the player as eligible for, in its own spelling. */
    static List<String> yahooEligible(List<String> eligiblePositions) {
        return eligiblePositions == null
                ? List.of()
                : eligiblePositions.stream().filter(InjuredReserve::isSlot)
                        .map(code -> code.toUpperCase(Locale.ROOT))
                        .distinct()
                        .toList();
    }

    /** ESPN's one IR slot, for a player out or on injured reserve; none for anyone else. */
    static List<String> espnEligible(String injuryStatus) {
        return injuryStatus != null && ESPN_ELIGIBLE.contains(injuryStatus.toUpperCase(Locale.ROOT))
                ? List.of("IR")
                : List.of();
    }
}
