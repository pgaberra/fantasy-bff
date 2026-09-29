package com.fantasy.bff.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fantasy.bff.dto.response.RosterSlots;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.RosterSlot;
import com.fantasy.bff.mapper.YahooLeagueSettingsMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeagueScoringTest {

    private static LeagueScoring league(RosterSlots slots) {
        return LeagueScoring.of(true, Map.of(), List.of(), slots, 12, 25, null);
    }

    private static RosterSlot slot(String position, int count) {
        return new RosterSlot().position(position).count(count);
    }

    /**
     * The standard Yahoo roster, with the injured-reserve spots Yahoo lists beside it: those
     * never reach the slots, so a team counts the sixteen players its lineup and bench hold.
     */
    @Test
    @DisplayName("a standard Yahoo roster counts sixteen, its IR, IR+ and NA spots left out")
    void standardYahooRosterCountsSixteen() {
        LeagueSettingsResponse settings = new LeagueSettingsResponse()
                .leagueKey("465.l.1")
                .name("League")
                .scoringType("head")
                .statCategories(List.of())
                .rosterPositions(List.of(
                        slot("C", 2), slot("LW", 2), slot("RW", 2), slot("D", 4), slot("G", 2),
                        slot("BN", 4), slot("IR", 1), slot("IR+", 2), slot("NA", 1)));

        RosterSlots slots = new YahooLeagueSettingsMapper().toProjectionSettings(settings, 12).rosterSlots();

        assertThat(league(slots).countedPlayers()).isEqualTo(16);
    }

    @Test
    @DisplayName("every lineup and bench spot counts, the flex included")
    void countsEverySpot() {
        assertThat(league(new RosterSlots(2, 2, 2, 0, 0, 4, 2, 5, 2)).countedPlayers()).isEqualTo(19);
    }

    @Test
    @DisplayName("the forward and wing flex slots count, and size the skater pool")
    void countsTheForwardFlexSlots() {
        LeagueScoring forwardsOnly = league(new RosterSlots(0, 0, 0, 0, 9, 5, 1, 5, 2));

        assertThat(forwardsOnly.countedPlayers()).isEqualTo(22);
        assertThat(forwardsOnly.skaterPoolSize()).isEqualTo(12 * 20);
        assertThat(league(new RosterSlots(2, 2, 2, 2, 0, 4, 0, 4, 2)).skaterPoolSize()).isEqualTo(12 * 16);
    }

    @Test
    @DisplayName("a league whose slots add up to nothing counts sixteen")
    void fallsBackToSixteen() {
        assertThat(league(new RosterSlots(0, 0, 0, 0, 0, 0, 0, 0, 0)).countedPlayers()).isEqualTo(16);
    }
}
