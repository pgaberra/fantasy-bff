package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fantasy.bff.dto.response.PlayerAvailability;
import com.fantasy.bff.service.LeagueAvailablePlayers.Available;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The wire as the pool less the league's rosters, with the platform's waivers laid over it. */
class LeagueAvailablePlayersTest {

    @Test
    void everyPoolPlayerNoTeamHoldsIsAvailableAFreeAgentUnlessOnWaivers() {
        List<Available> wire = LeagueAvailablePlayers.unrostered(
                List.of(
                        pooled("1", "Held Star"),
                        pooled("2", "William Eklund"),
                        pooled("3", "Just Dropped")),
                List.of(platform("3", "Just Dropped", PlayerAvailability.WAIVERS)),
                Set.of("1"));

        assertThat(wire).extracting(Available::playerId).containsExactly("2", "3");
        assertThat(wire).extracting(Available::availability)
                .containsExactly(PlayerAvailability.FREE_AGENT, PlayerAvailability.WAIVERS);
    }

    @Test
    void aWaiverRowCarriesThePlatformsOwnClub() {
        Available pooled = new Available(
                "3", "Just Dropped", "SJ", List.of("LW"), false, 72, PlayerAvailability.UNKNOWN, 4.0);
        Available onWaivers = new Available(
                "3", "Just Dropped", "Ana", List.of("LW", "RW"), false, 72, PlayerAvailability.WAIVERS, null);

        Available listed = LeagueAvailablePlayers.unrostered(List.of(pooled), List.of(onWaivers), Set.of())
                .getFirst();

        assertThat(listed.teamAbbrev()).isEqualTo("Ana");
        assertThat(listed.positions()).containsExactly("LW", "RW");
    }

    @Test
    void aPlayerOnWaiversThePoolDoesNotHaveYetIsStillListed() {
        List<Available> wire = LeagueAvailablePlayers.unrostered(
                List.of(pooled("2", "William Eklund")),
                List.of(platform("9", "Fresh Call-Up", PlayerAvailability.WAIVERS)),
                Set.of());

        assertThat(wire).extracting(Available::playerId).containsExactly("2", "9");
    }

    @Test
    void onlyTheRowsThePlatformMarksAsOnWaiversCount() {
        // A platform service that ignored the waivers filter sends free agents too; their own
        // status keeps them from being called waivers, and a rostered one is never listed.
        List<Available> wire = LeagueAvailablePlayers.unrostered(
                List.of(pooled("2", "William Eklund")),
                List.of(
                        platform("2", "William Eklund", PlayerAvailability.FREE_AGENT),
                        platform("1", "Held Star", PlayerAvailability.WAIVERS)),
                Set.of("1"));

        assertThat(wire).extracting(Available::playerId).containsExactly("2");
        assertThat(wire.getFirst().availability()).isEqualTo(PlayerAvailability.FREE_AGENT);
    }

    @Test
    void aRowFromTheWaiversReadWithoutAnOwnershipIsStillOnWaivers() {
        List<Available> wire = LeagueAvailablePlayers.unrostered(
                List.of(pooled("3", "Just Dropped")),
                List.of(platform("3", "Just Dropped", PlayerAvailability.UNKNOWN)),
                Set.of());

        assertThat(wire.getFirst().availability()).isEqualTo(PlayerAvailability.WAIVERS);
    }

    @Test
    void aFreeAgentFromThePoolCarriesNoPercentRostered() {
        Available pooled = new Available(
                "2", "William Eklund", "SJ", List.of("LW"), false, 72, PlayerAvailability.UNKNOWN, 3.0);

        assertThat(LeagueAvailablePlayers.unrostered(List.of(pooled), List.of(), Set.of()).getFirst()
                        .percentRostered())
                .isNull();
    }

    private static Available pooled(String id, String name) {
        return new Available(id, name, "SJ", List.of("LW"), false, null, PlayerAvailability.UNKNOWN, null);
    }

    private static Available platform(String id, String name, PlayerAvailability availability) {
        return new Available(id, name, "SJ", List.of("LW"), false, null, availability, null);
    }
}
