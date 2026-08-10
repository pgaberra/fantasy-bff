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

    private static PlayerStatLine line(long id, String fullName, String position, int jersey) {
        return line(id, fullName, position).sweaterNumber(jersey);
    }

    private static Map<Integer, PlayerStatLine> match(List<PlayerStatLine> lines, Subject... subjects) {
        return new EspnStatLineIndex(lines).matchAll(List.of(subjects));
    }

    @Test
    void matchesOnNameAndPosition() {
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Connor McDavid", "C")),
                new Subject(97, "Connor McDavid", "C", null));

        assertThat(matched.get(97)).extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void foldsAccentsAndPunctuationTheTwoSourcesSpellDifferently() {
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Juraj Slafkovsky", "LW"), line(2L, "Ryan OReilly", "C")),
                new Subject(20, "Juraj Slafkovský", "LW", null),
                new Subject(90, "Ryan O'Reilly", "C", null));

        assertThat(matched).containsOnlyKeys(20, 90);
    }

    @Test
    void fallsBackToTheNameAloneWhenThePositionsDisagree() {
        // Yahoo lists Zack Bolduc at LW, ESPN at C. The position is a tie break, not a filter.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Zack Bolduc", "C")),
                new Subject(76, "Zack Bolduc", "LW", null));

        assertThat(matched.get(76)).extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void separatesTwoPlayersWhoShareANameByPosition() {
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Sebastian Aho", "C"), line(2L, "Sebastian Aho", "D")),
                new Subject(20, "Sebastian Aho", "C", null),
                new Subject(28, "Sebastian Aho", "D", null));

        assertThat(matched.get(20)).extracting(PlayerStatLine::getId).isEqualTo(1L);
        assertThat(matched.get(28)).extracting(PlayerStatLine::getId).isEqualTo(2L);
    }

    @Test
    void recoversAFamiliarFirstNameFromTheLastNameAndInitial() {
        // Yahoo's Zachary for ESPN's Zack, Freddy for Frederick, Samuel for Sammy.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Zack Bolduc", "C")),
                new Subject(76, "Zachary Bolduc", "C", null));

        assertThat(matched.get(76)).extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void staysUnmatchedRatherThanGuessingBetweenTwoCandidates() {
        // Attaching another player's hat tricks is worse than showing none.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Sebastian Aho", "C"), line(2L, "Sebastian Aho", "D")),
                new Subject(20, "Sebastian Aho", "LW", null));

        assertThat(matched).isEmpty();
    }

    @Test
    void neverGivesOneStatLineToTwoPlayers() {
        // Found on staging: ESPN doesn't carry Tyce Thompson, so the familiar-name form
        // resolved him to Tage Thompson's season — a hat trick and 1856 shifts for a player
        // who barely played. The exact match keeps the line; the guess gets nothing.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Tage Thompson", "C")),
                new Subject(72, "Tage Thompson", "C", null),
                new Subject(41, "Tyce Thompson", "RW", null));

        assertThat(matched).containsOnlyKeys(72);
        assertThat(matched.get(72)).extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void aLineTwoPlayersMatchExactlyGoesToNeither() {
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Elias Pettersson", "D")),
                new Subject(40, "Elias Pettersson", "D", null),
                new Subject(25, "Elias Pettersson", "D", null));

        assertThat(matched).isEmpty();
    }

    /**
     * The three collisions actually present in last season's data. Each is a pair of real
     * players, and in each the jersey is the only thing that tells them apart — team doesn't
     * (both Petterssons play in Vancouver) and neither does position.
     */
    @Test
    void theJerseyDecidesWhichOfTwoSameNamedPlayersOwnsTheLine() {
        Map<Integer, PlayerStatLine> pettersson = match(
                List.of(line(1L, "Elias Pettersson", "C", 40)),
                new Subject(400, "Elias Pettersson", "C", 40),
                new Subject(250, "Elias Pettersson", "D", 25));
        assertThat(pettersson).containsOnlyKeys(400);

        Map<Integer, PlayerStatLine> murphy = match(
                List.of(line(2L, "Connor Murphy", "D", 5)),
                new Subject(5, "Connor Murphy", "D", 5),
                new Subject(81, "Connor Murphy", "D", 81));
        assertThat(murphy).containsOnlyKeys(5);
    }

    @Test
    void theJerseyPicksBetweenTwoStatLinesThatShareANameAndPosition() {
        // ESPN carries both Matt Murrays in goal; each Yahoo goalie must land on their own.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Matt Murray", "G", 30), line(2L, "Matt Murray", "G", 32)),
                new Subject(300, "Matt Murray", "G", 30),
                new Subject(320, "Matt Murray", "G", 32));

        assertThat(matched.get(300)).extracting(PlayerStatLine::getId).isEqualTo(1L);
        assertThat(matched.get(320)).extracting(PlayerStatLine::getId).isEqualTo(2L);
    }

    @Test
    void aPlayerEspnDoesNotCarryStillGetsNothing() {
        // Only the number 40 Pettersson exists on ESPN's side; the other must not inherit him.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Elias Pettersson", "C", 40)),
                new Subject(250, "Elias Pettersson", "D", 25));

        assertThat(matched).isEmpty();
    }

    @Test
    void anUnknownJerseyIsNeverAFilter() {
        // A player whose number we don't have still matches when the name is unambiguous.
        Map<Integer, PlayerStatLine> matched = match(
                List.of(line(1L, "Connor McDavid", "C", 97)),
                new Subject(970, "Connor McDavid", "C", null));

        assertThat(matched).containsOnlyKeys(970);
    }

    @Test
    void emptyIndexMatchesNobody() {
        assertThat(EspnStatLineIndex.empty().matchAll(List.of(new Subject(97, "Connor McDavid", "C", null))))
                .isEmpty();
    }
}
