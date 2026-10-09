package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam;
import com.fantasy.bff.generated.yahoo.model.LeagueRostersResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class YahooLeagueRosterServiceTest {

    private final YahooServiceClient client = mock(YahooServiceClient.class);
    private final YahooLeagueRosterService service = new YahooLeagueRosterService(client);

    @Test
    void keysEachTeamsPlayersByItsTeamKeyInYahoosOrder() {
        when(client.rosters("user-1", "465.l.1")).thenReturn(new LeagueRostersResponse()
                .leagueKey("465.l.1")
                .teams(List.of(
                        new LeagueRosterTeam().teamKey("465.l.1.t.2").name("Bravo").mine(false).players(List.of(
                                new LeagueRosterPlayer().playerKey("465.p.9").playerId(9).selectedPosition("C"),
                                new LeagueRosterPlayer().playerKey("465.p.4").playerId(4).selectedPosition("IR"))),
                        new LeagueRosterTeam().teamKey("465.l.1.t.3").name("Charlie").mine(false).players(List.of(
                                new LeagueRosterPlayer().playerKey("465.p.7").playerId(7).selectedPosition("NA"),
                                new LeagueRosterPlayer().playerKey("465.p.8").playerId(8).selectedPosition("BN"))),
                        new LeagueRosterTeam().teamKey("465.l.1.t.1").name("Alpha").mine(true).players(List.of()))));

        var rosters = service.rosters("user-1", "465.l.1");

        assertThat(rosters.players().keySet()).containsExactly("465.l.1.t.2", "465.l.1.t.3", "465.l.1.t.1");
        assertThat(rosters.players().get("465.l.1.t.2")).containsExactly(9, 4);
        assertThat(rosters.players().get("465.l.1.t.1")).isEmpty();
        assertThat(rosters.reserve()).containsExactlyInAnyOrder(4, 7);
    }
}
