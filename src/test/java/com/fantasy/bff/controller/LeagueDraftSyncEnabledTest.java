package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.generated.yahoo.model.LeagueDraftPick;
import com.fantasy.bff.generated.yahoo.model.LeagueDraftResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueDraftTeam;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With DRAFT_LEAGUE_SYNC_ENABLED=true over the Yahoo pool, a league's draft is served to any signed-in user. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"league-draft-sync.enabled=true", "players.source=yahoo"})
class LeagueDraftSyncEnabledTest extends BaseIntegrationTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private YahooServiceClient yahooServiceClient;

    @Test
    void servesTheDraftForTheSignedInUser() throws Exception {
        when(yahooServiceClient.draft(USER_ID, "465.l.9")).thenReturn(new LeagueDraftResponse()
                .leagueKey("465.l.9")
                .status(LeagueDraftResponse.StatusEnum.IN_PROGRESS)
                .auction(false)
                .teams(List.of(new LeagueDraftTeam().teamKey("465.l.9.t.1").name("Alpha").mine(true)))
                .picks(List.of(new LeagueDraftPick().pick(1).round(1).teamKey("465.l.9.t.1")
                        .playerKey("465.p.6743").playerId(6743))));

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.9/draft")
                        .header("Authorization", "Bearer " + jwtTokenValidator.generateToken(USER_ID, "a@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.teams[0].id").value("465.l.9.t.1"))
                .andExpect(jsonPath("$.teams[0].mine").value(true))
                .andExpect(jsonPath("$.picks[0].overall").value(1))
                .andExpect(jsonPath("$.picks[0].playerId").value(6743));
    }

    @Test
    void reportsTheFeatureAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leagueDraftSync").value(true));
    }

    @Test
    void refusesASignedOutRequest() throws Exception {
        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.9/draft"))
                .andExpect(status().isUnauthorized());
    }
}
