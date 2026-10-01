package com.fantasy.bff.controller;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.generated.espn.model.LeagueRostersResponse.StatusEnum;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam;
import com.fantasy.bff.generated.yahoo.model.LeagueRostersResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import com.fantasy.bff.service.EspnPoolIdCrosswalk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With WHOS_HOT_AVAILABLE_FILTER_ENABLED=true over the Yahoo pool: every player any team in the
 * league holds, once, in the pool's numbering, whichever platform the league is on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"whos-hot.available-filter.enabled=true", "players.source=yahoo"})
class LeagueRosteredPlayersTest extends BaseIntegrationTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private YahooServiceClient yahooServiceClient;
    @MockitoBean private EspnServiceClient espnServiceClient;
    @MockitoBean private EspnPoolIdCrosswalk crosswalk;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtTokenValidator.generateToken(USER_ID, "a@example.com");
    }

    @Test
    void servesEveryYahooTeamsPlayersAsOneSet() throws Exception {
        when(yahooServiceClient.rosters(USER_ID, "465.l.9")).thenReturn(new LeagueRostersResponse()
                .teams(List.of(
                        new LeagueRosterTeam().teamKey("465.l.9.t.1").players(List.of(
                                new LeagueRosterPlayer().playerId(6743),
                                new LeagueRosterPlayer().playerId(4001))),
                        new LeagueRosterTeam().teamKey("465.l.9.t.2").players(List.of(
                                new LeagueRosterPlayer().playerId(7109))))));

        mockMvc.perform(get("/api/v1/leagues/rostered-players?platform=YAHOO&leagueId=465.l.9")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerIds.length()").value(3))
                .andExpect(jsonPath("$.playerIds[0]").value(4001))
                .andExpect(jsonPath("$.playerIds[1]").value(6743))
                .andExpect(jsonPath("$.playerIds[2]").value(7109));
        verifyNoInteractions(espnServiceClient);
    }

    @Test
    void servesAnEspnLeagueInThePoolsNumberingAndDropsWhoTheyCannotPlace() throws Exception {
        when(espnServiceClient.rosters(USER_ID, "123"))
                .thenReturn(new com.fantasy.bff.generated.espn.model.LeagueRostersResponse()
                        .leagueId("123")
                        .season(2027)
                        .status(StatusEnum.FINISHED)
                        .teams(List.of(
                                new com.fantasy.bff.generated.espn.model.LeagueRosterTeam()
                                        .teamId(1).name("Alpha").mine(true).playerIds(List.of(4001L, 4002L)),
                                new com.fantasy.bff.generated.espn.model.LeagueRosterTeam()
                                        .teamId(2).name("Bravo").mine(false).playerIds(List.of(4003L)))));
        // 4002 has no counterpart in the pool, so no row on a board can be him.
        when(crosswalk.poolIds(anyCollection())).thenReturn(Map.of(4001L, 6743, 4003L, 7109));

        mockMvc.perform(get("/api/v1/leagues/rostered-players?platform=ESPN&leagueId=123")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerIds.length()").value(2))
                .andExpect(jsonPath("$.playerIds[0]").value(6743))
                .andExpect(jsonPath("$.playerIds[1]").value(7109));
        verifyNoInteractions(yahooServiceClient);
    }

    @Test
    void refusesAnOversizedLeagueId() throws Exception {
        mockMvc.perform(get("/api/v1/leagues/rostered-players?platform=YAHOO&leagueId=" + "9".repeat(65))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(yahooServiceClient);
    }

    @Test
    void refusesASignedOutCaller() throws Exception {
        mockMvc.perform(get("/api/v1/leagues/rostered-players?platform=YAHOO&leagueId=465.l.9"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsTheFeatureAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whosHotAvailableFilter").value(true));
    }
}
