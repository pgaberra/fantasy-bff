package com.fantasy.bff.service.mapping;

import com.fantasy.bff.generated.espn.model.PlayerStatLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EspnStatLineIndexTest {

    private static PlayerStatLine line(long id, String fullName, String position) {
        return new PlayerStatLine().id(id).fullName(fullName).position(position);
    }

    @Test
    void matchesOnNameAndPosition() {
        EspnStatLineIndex index = new EspnStatLineIndex(List.of(line(1L, "Connor McDavid", "C")));

        assertThat(index.find("Connor McDavid", "C")).get()
                .extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void foldsAccentsAndPunctuationTheTwoSourcesSpellDifferently() {
        EspnStatLineIndex index = new EspnStatLineIndex(List.of(
                line(1L, "Juraj Slafkovsky", "LW"),
                line(2L, "Ryan OReilly", "C")));

        assertThat(index.find("Juraj Slafkovský", "LW")).isPresent();
        assertThat(index.find("Ryan O'Reilly", "C")).isPresent();
    }

    @Test
    void fallsBackToTheNameAloneWhenThePositionsDisagree() {
        // Yahoo lists Zack Bolduc at LW, ESPN at C. The position is a tie break, not a filter.
        EspnStatLineIndex index = new EspnStatLineIndex(List.of(line(1L, "Zack Bolduc", "C")));

        assertThat(index.find("Zack Bolduc", "LW")).get()
                .extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void separatesTwoPlayersWhoShareANameByPosition() {
        EspnStatLineIndex index = new EspnStatLineIndex(List.of(
                line(1L, "Sebastian Aho", "C"),
                line(2L, "Sebastian Aho", "D")));

        assertThat(index.find("Sebastian Aho", "C")).get()
                .extracting(PlayerStatLine::getId).isEqualTo(1L);
        assertThat(index.find("Sebastian Aho", "D")).get()
                .extracting(PlayerStatLine::getId).isEqualTo(2L);
    }

    @Test
    void recoversAFamiliarFirstNameFromTheLastNameAndInitial() {
        // Yahoo's Zachary for ESPN's Zack, Freddy for Frederick, Samuel for Sammy.
        EspnStatLineIndex index = new EspnStatLineIndex(List.of(line(1L, "Zack Bolduc", "C")));

        assertThat(index.find("Zachary Bolduc", "C")).get()
                .extracting(PlayerStatLine::getId).isEqualTo(1L);
    }

    @Test
    void staysUnmatchedRatherThanGuessingBetweenTwoCandidates() {
        // Attaching another player's hat tricks is worse than showing none.
        EspnStatLineIndex index = new EspnStatLineIndex(List.of(
                line(1L, "Sebastian Aho", "C"),
                line(2L, "Sebastian Aho", "D")));

        assertThat(index.find("Sebastian Aho", "LW")).isEmpty();
    }

    @Test
    void emptyIndexMatchesNobody() {
        assertThat(EspnStatLineIndex.empty().find("Connor McDavid", "C")).isEmpty();
    }
}
