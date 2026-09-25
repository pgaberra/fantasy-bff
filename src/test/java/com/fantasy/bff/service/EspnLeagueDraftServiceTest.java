package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.LeagueDraftPick;
import com.fantasy.bff.dto.response.LeagueDraftResponse;
import com.fantasy.bff.dto.response.LeagueDraftStatus;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import com.fantasy.bff.generated.espn.model.LeagueDraftResponse.StatusEnum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EspnLeagueDraftServiceTest {

    private static final String USER_ID = "user-1";
    private static final String LEAGUE_ID = "123";

    private final EspnServiceClient client = mock(EspnServiceClient.class);
    private final EspnPoolIdCrosswalk crosswalk = mock(EspnPoolIdCrosswalk.class);
    private final LeagueDraftSyncAvailability availability = mock(LeagueDraftSyncAvailability.class);
    private final EspnLeagueDraftService service = new EspnLeagueDraftService(client, crosswalk, availability);

    private static com.fantasy.bff.generated.espn.model.LeagueDraftPick pick(
            int overall, int round, int team, long playerId) {
        return new com.fantasy.bff.generated.espn.model.LeagueDraftPick()
                .pick(overall).round(round).teamId(team).playerId(playerId).keeper(false);
    }

    private static com.fantasy.bff.generated.espn.model.LeagueDraftResponse draft(
            StatusEnum status, List<com.fantasy.bff.generated.espn.model.LeagueDraftPick> picks) {
        return new com.fantasy.bff.generated.espn.model.LeagueDraftResponse()
                .leagueId(LEAGUE_ID)
                .season(2027)
                .status(status)
                .auction(false)
                .teams(List.of(
                        new com.fantasy.bff.generated.espn.model.LeagueDraftTeam().teamId(2).name("Bravo").mine(false),
                        new com.fantasy.bff.generated.espn.model.LeagueDraftTeam().teamId(1).name("Alpha").mine(true)))
                .orderKnown(true)
                .picks(picks);
    }

    @Test
    void servesTheDraftWithPicksInThePoolsNumbering() {
        when(availability.espnAvailable()).thenReturn(true);
        when(client.draft(USER_ID, LEAGUE_ID)).thenReturn(draft(StatusEnum.IN_PROGRESS, List.of(
                pick(2, 1, 1, 4002),
                pick(1, 1, 2, 4001),
                pick(3, 2, 1, 4003),
                pick(5, 3, 2, 4005))));
        when(crosswalk.poolIds(List.of(4001L, 4002L, 4003L))).thenReturn(Map.of(4001L, 6743, 4003L, 7109));

        LeagueDraftResponse response = service.draft(USER_ID, LEAGUE_ID);

        assertThat(response.status()).isEqualTo(LeagueDraftStatus.IN_PROGRESS);
        assertThat(response.orderKnown()).isTrue();
        assertThat(response.teams()).containsExactly(
                new LeagueDraftTeam("espn.l.123.t.2", "Bravo", false),
                new LeagueDraftTeam("espn.l.123.t.1", "Alpha", true));
        // Pick 4 is missing, so pick 5 waits; pick 2's player has no pool counterpart and keeps
        // his place under the negative of his ESPN id.
        assertThat(response.picks()).containsExactly(
                new LeagueDraftPick(1, 1, "espn.l.123.t.2", 6743),
                new LeagueDraftPick(2, 1, "espn.l.123.t.1", -4002),
                new LeagueDraftPick(3, 2, "espn.l.123.t.1", 7109));
    }

    @Test
    void asksForNoCrosswalkBeforeTheFirstPick() {
        when(availability.espnAvailable()).thenReturn(true);
        when(client.draft(USER_ID, LEAGUE_ID)).thenReturn(draft(StatusEnum.PRE_DRAFT, List.of()));
        when(crosswalk.poolIds(List.of())).thenReturn(Map.of());

        LeagueDraftResponse response = service.draft(USER_ID, LEAGUE_ID);

        assertThat(response.status()).isEqualTo(LeagueDraftStatus.PRE_DRAFT);
        assertThat(response.picks()).isEmpty();
    }

    @Test
    void readsAMissingStatusAsUnknown() {
        when(availability.espnAvailable()).thenReturn(true);
        when(client.draft(USER_ID, LEAGUE_ID)).thenReturn(draft(null, List.of()));
        when(crosswalk.poolIds(any())).thenReturn(Map.of());

        assertThat(service.draft(USER_ID, LEAGUE_ID).status()).isEqualTo(LeagueDraftStatus.UNKNOWN);
    }

    @Test
    void refusesWhereTheFeatureIsOffWithoutAskingEspn() {
        when(availability.espnAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service.draft(USER_ID, LEAGUE_ID)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(client);
        verify(crosswalk, never()).poolIds(any());
    }
}
