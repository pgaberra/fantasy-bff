package com.fantasy.bff.service.mapping;

import com.fantasy.bff.generated.espn.model.PlayerStatLine;
import com.fantasy.bff.service.mapping.EspnStatLineIndex.Subject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EspnStatLineIndexTest {

    private static PlayerStatLine line(long id, String fullName, String position) {
        return new PlayerStatLine().id(id).fullName(fullName).position(position);
    }

    private static Map<Integer, PlayerStatLine> match(List<PlayerStatLine> lines, Subject... subjects) {
        return new EspnStatLineIndex(lines).matchAll(List.of(subjects));
    }

    @Test
    void matchesOnNameAndPosition() {
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Connor McDavid", "C")),
                new Subject(97, "Connor McDavid", "C"));

        assertThat(matched.get(97)).extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void foldsAccentsAndPunctuationTheTwoSourcesSpellDifferently() {
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Juraj Slafkovsky", "LW"), line(2L, "Ryan OReilly", "C")),
                new Subject(20, "Juraj Slafkovský", "LW"),
                new Subject(90, "Ryan O'Reilly", "C"));

        assertThat(matched).containsOnlyKeys(20, 90);
    }

    @Test
    void fallsBackToTheNameAloneWhenThePositionsDisagree() {
        // Yahoo lists Zack Bolduc at LW, ESPN at C. The position is a tie break, not a filter.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Zack Bolduc", "C")),
                new Subject(76, "Zack Bolduc", "LW"));

        assertThat(matched.get(76)).extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void separatesTwoPlayersWhoShareANameByPosition() {
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Sebastian Aho", "C"), line(2L, "Sebastian Aho", "D")),
                new Subject(20, "Sebastian Aho", "C"),
                new Subject(28, "Sebastian Aho", "D"));

        assertThat(matched.get(20)).extracting(PlayerStatLine::getId).isEqualTo(1L);
        assertThat(matched.get(28)).extracting(PlayerStatLine::getId).isEqualTo(2L);
    }

    @Test
    void recoversAFamiliarFirstNameFromTheLastNameAndInitial() {
        // Yahoo's Zachary for ESPN's Zack, Freddy for Frederick, Samuel for Sammy.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Zack Bolduc", "C")),
                new Subject(76, "Zachary Bolduc", "C"));

        assertThat(matched.get(76)).extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void staysUnmatchedRatherThanGuessingBetweenTwoCandidates() {
        // Attaching another player's hat tricks is worse than showing none.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Sebastian Aho", "C"), line(2L, "Sebastian Aho", "D")),
                new Subject(20, "Sebastian Aho", "LW"));

        assertThat(matched).isEmpty();
    }

    @Test
    void neverGivesOneStatLineToTwoPlayers() {
        // Found on staging: ESPN doesn't carry Tyce Thompson, so the familiar-name form
        // resolved him to Tage Thompson's season — a hat trick and 1856 shifts for a player
        // who barely played. The exact match keeps the line; the guess gets nothing.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Tage Thompson", "C")),
                new Subject(72, "Tage Thompson", "C"),
                new Subject(41, "Tyce Thompson", "RW"));

        assertThat(matched).containsOnlyKeys(72);
        assertThat(matched.get(72)).extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void aLineTwoPlayersMatchExactlyGoesToNeither() {
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Elias Pettersson", "D")),
                new Subject(40, "Elias Pettersson", "D"),
                new Subject(25, "Elias Pettersson", "D"));

        assertThat(matched).isEmpty();
    }

    @Test
    void emptyIndexMatchesNobody() {
        assertThat(EspnStatLineIndex.empty().matchAll(List.of(new Subject(97, "Connor McDavid", "C"))))
                .isEmpty();
    }
}
