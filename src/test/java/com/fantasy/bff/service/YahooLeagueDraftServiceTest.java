package com.fantasy.bff.service;

import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.LeagueDraftResponse;
import com.fantasy.bff.dto.response.LeagueDraftStatus;
import com.fantasy.bff.generated.yahoo.model.LeagueDraftPick;
import com.fantasy.bff.generated.yahoo.model.LeagueDraftTeam;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class YahooLeagueDraftServiceTest {

    private static final String USER_ID = "user-1";
    private static final String LEAGUE_KEY = "465.l.9";

    private final YahooServiceClient client = mock(YahooServiceClient.class);
    private final LeagueDraftSyncAvailability availability = mock(LeagueDraftSyncAvailability.class);
    private final YahooLeagueDraftService service = new YahooLeagueDraftService(client, availability);

    private static LeagueDraftPick pick(int overall, int round, String team, Integer playerId) {
        return new LeagueDraftPick().pick(overall).round(round).teamKey(team)
                .playerKey(playerId == null ? null : "465.p." + playerId).playerId(playerId);
    }

    @Test
    void passesTeamsAndTheUnbrokenRunOfMadePicks() {
        when(availability.available()).thenReturn(true);
        when(client.draft(USER_ID, LEAGUE_KEY)).thenReturn(
                new com.fantasy.bff.generated.yahoo.model.LeagueDraftResponse()
                        .leagueKey(LEAGUE_KEY)
                        .status(com.fantasy.bff.generated.yahoo.model.LeagueDraftResponse.StatusEnum.IN_PROGRESS)
                        .auction(false)
                        .teams(List.of(
                                new LeagueDraftTeam().teamKey("465.l.9.t.2").name("Bravo").mine(false),
                                new LeagueDraftTeam().teamKey("465.l.9.t.1").name("Alpha").mine(true)))
                        .orderKnown(true)
                        .picks(List.of(
                                pick(2, 1, "465.l.9.t.1", 7109),
                                pick(1, 1, "465.l.9.t.2", 6743),
                                pick(3, 2, "465.l.9.t.1", null),
                                pick(4, 2, "465.l.9.t.2", 5000))));

        LeagueDraftResponse draft = service.draft(USER_ID, LEAGUE_KEY);

        assertThat(draft.status()).isEqualTo(LeagueDraftStatus.IN_PROGRESS);
        assertThat(draft.auction()).isFalse();
        assertThat(draft.teams()).extracting("id").containsExactly("465.l.9.t.2", "465.l.9.t.1");
        assertThat(draft.teams().get(1).mine()).isTrue();
        assertThat(draft.orderKnown()).isTrue();
        assertThat(draft.picks()).extracting("overall").containsExactly(1, 2);
        assertThat(draft.picks()).extracting("playerId").containsExactly(6743, 7109);
        assertThat(draft.picks()).extracting("teamId").containsExactly("465.l.9.t.2", "465.l.9.t.1");
    }

    /**
     * Before a live draft runs, Yahoo lists its teams in an order of its own, which is not who picks
     * when. The list alone looks the same either way, so whether it is the draft order has to travel
     * beside it, and a Yahoo answer that does not say is read as not known.
     */
    @Test
    void saysWhenTheTeamOrderIsNotTheDraftOrder() {
        when(availability.available()).thenReturn(true);
        var unknown = new com.fantasy.bff.generated.yahoo.model.LeagueDraftResponse()
                .leagueKey(LEAGUE_KEY)
                .status(com.fantasy.bff.generated.yahoo.model.LeagueDraftResponse.StatusEnum.PRE_DRAFT)
                .auction(false)
                .teams(List.of(
                        new LeagueDraftTeam().teamKey("465.l.9.t.1").name("Alpha").mine(true),
                        new LeagueDraftTeam().teamKey("465.l.9.t.2").name("Bravo").mine(false)))
                .orderKnown(false)
                .picks(List.of());
        when(client.draft(USER_ID, LEAGUE_KEY)).thenReturn(unknown);

        LeagueDraftResponse draft = service.draft(USER_ID, LEAGUE_KEY);

        assertThat(draft.orderKnown()).isFalse();
        assertThat(draft.teams()).extracting("id").containsExactly("465.l.9.t.1", "465.l.9.t.2");

        unknown.setOrderKnown(null);
        assertThat(service.draft(USER_ID, LEAGUE_KEY).orderKnown()).isFalse();
    }

    @Test
    void readsAMissingStatusAsUnknown() {
        when(availability.available()).thenReturn(true);
        when(client.draft(USER_ID, LEAGUE_KEY)).thenReturn(
                new com.fantasy.bff.generated.yahoo.model.LeagueDraftResponse()
                        .leagueKey(LEAGUE_KEY).auction(true).teams(List.of()).picks(List.of()));

        LeagueDraftResponse draft = service.draft(USER_ID, LEAGUE_KEY);

        assertThat(draft.status()).isEqualTo(LeagueDraftStatus.UNKNOWN);
        assertThat(draft.auction()).isTrue();
        assertThat(draft.picks()).isEmpty();
    }

    @Test
    void refusesWithoutAskingYahooWhereTheFeatureIsOff() {
        when(availability.available()).thenReturn(false);

        assertThatThrownBy(() -> service.draft(USER_ID, LEAGUE_KEY)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(client);
    }
}
