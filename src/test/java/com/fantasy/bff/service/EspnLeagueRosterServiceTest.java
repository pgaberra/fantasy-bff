package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.LeagueDraftStatus;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import com.fantasy.bff.generated.espn.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.espn.model.LeagueRosterTeam;
import com.fantasy.bff.generated.espn.model.LeagueRostersResponse;
import com.fantasy.bff.generated.espn.model.LeagueRostersResponse.StatusEnum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EspnLeagueRosterServiceTest {

    private static final String USER_ID = "user-1";
    private static final String LEAGUE_ID = "123";

    private final EspnServiceClient client = mock(EspnServiceClient.class);
    private final EspnPoolIdCrosswalk crosswalk = mock(EspnPoolIdCrosswalk.class);
    private final EspnLeagueRosterService service = new EspnLeagueRosterService(client, crosswalk);

    private static LeagueRostersResponse rosters(StatusEnum status, List<Long> alpha, List<Long> bravo) {
        return new LeagueRostersResponse()
                .leagueId(LEAGUE_ID)
                .season(2027)
                .status(status)
                .teams(List.of(
                        new LeagueRosterTeam().teamId(1).name("Alpha").mine(true).playerIds(alpha),
                        new LeagueRosterTeam().teamId(2).name("Bravo").mine(false).playerIds(bravo)));
    }

    @Test
    void servesEachTeamsPlayersInThePoolsNumbering() {
        when(client.rosters(USER_ID, LEAGUE_ID))
                .thenReturn(rosters(StatusEnum.FINISHED, List.of(4001L, 4002L), List.of(4003L)));
        when(crosswalk.poolIds(List.of(4001L, 4002L, 4003L))).thenReturn(Map.of(4001L, 6743, 4003L, 7109));

        EspnLeagueRosterService.Rosters result = service.rosters(USER_ID, LEAGUE_ID);

        assertThat(result.status()).isEqualTo(LeagueDraftStatus.FINISHED);
        assertThat(result.teams()).containsExactly(
                new LeagueDraftTeam("espn.l.123.t.1", "Alpha", true),
                new LeagueDraftTeam("espn.l.123.t.2", "Bravo", false));
        // 4002 has no pool counterpart and is kept under the negative of his ESPN id.
        assertThat(result.players()).containsExactly(
                Map.entry("espn.l.123.t.1", List.of(6743, -4002)),
                Map.entry("espn.l.123.t.2", List.of(7109)));
        assertThat(result.reserve()).isEmpty();
    }

    @Test
    void marksThePlayersParkedOnInjuredReserve() {
        LeagueRostersResponse response = rosters(StatusEnum.FINISHED, List.of(4001L, 4002L), List.of(4003L));
        response.getTeams().get(0).players(List.of(
                new LeagueRosterPlayer().espnId(4001L).lineupSlot("IR"),
                new LeagueRosterPlayer().espnId(4002L).lineupSlot("IR")));
        response.getTeams().get(1).players(List.of(new LeagueRosterPlayer().espnId(4003L).lineupSlot("BN")));
        when(client.rosters(USER_ID, LEAGUE_ID)).thenReturn(response);
        when(crosswalk.poolIds(List.of(4001L, 4002L, 4003L))).thenReturn(Map.of(4001L, 6743, 4003L, 7109));

        EspnLeagueRosterService.Rosters result = service.rosters(USER_ID, LEAGUE_ID);

        // Numbered as the players are: the pool's id, or the negative of an unmatched ESPN id.
        assertThat(result.reserve()).containsExactlyInAnyOrder(6743, -4002);
    }

    @Test
    void asksForNoCrosswalkBeforeTheDraft() {
        when(client.rosters(USER_ID, LEAGUE_ID))
                .thenReturn(rosters(StatusEnum.PRE_DRAFT, List.of(), List.of()));

        EspnLeagueRosterService.Rosters result = service.rosters(USER_ID, LEAGUE_ID);

        assertThat(result.status()).isEqualTo(LeagueDraftStatus.PRE_DRAFT);
        assertThat(result.players().values()).allSatisfy(players -> assertThat(players).isEmpty());
        verifyNoInteractions(crosswalk);
    }
}
