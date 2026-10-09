package com.fantasy.bff.service.scoring;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The NHL schedule as a league's lineups are set against it: the nights each club plays over the
 * stretch being ranked, and how many games it plays in the whole season.
 *
 * <p>Clubs are spelled the way the player pool spells them, so a player's {@code team} finds his
 * club's nights directly.
 *
 * @param window the dates each club plays over the stretch being ranked: the rest of the season
 *     once it is under way, the whole season otherwise
 * @param seasonGames each club's games over the whole season, which a line's games played is a
 *     share of
 */
public record LeagueSchedule(Map<String, List<LocalDate>> window, Map<String, Integer> seasonGames) {

    public LeagueSchedule {
        window = window.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        seasonGames = Map.copyOf(seasonGames);
    }

    /** Every date any club plays in the stretch, earliest first. */
    List<LocalDate> nights() {
        TreeSet<LocalDate> nights = new TreeSet<>();
        window.values().forEach(nights::addAll);
        return List.copyOf(nights);
    }
}
