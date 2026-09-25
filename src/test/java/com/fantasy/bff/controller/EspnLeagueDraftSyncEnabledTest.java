package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.generated.espn.model.LeagueDraftPick;
import com.fantasy.bff.generated.espn.model.LeagueDraftResponse;
import com.fantasy.bff.generated.espn.model.LeagueDraftTeam;
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

/**
 * With ESPN_DRAFT_LEAGUE_SYNC_ENABLED=true an ESPN league's draft is served, over an ESPN pool
 * here so its ids pass straight through; Yahoo's switch is independent of it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"league-draft-sync.espn-enabled=true", "players.source=espn"})
class EspnLeagueDraftSyncEnabledTest extends BaseIntegrationTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private EspnServiceClient espnServiceClient;

    @Test
    void servesTheDraftForTheSignedInUser() throws Exception {
        when(espnServiceClient.draft(USER_ID, "123")).thenReturn(new LeagueDraftResponse()
                .leagueId("123")
                .season(2027)
                .status(LeagueDraftResponse.StatusEnum.IN_PROGRESS)
                .auction(false)
                .teams(List.of(new LeagueDraftTeam().teamId(1).name("Alpha").mine(true)))
                .orderKnown(true)
                .picks(List.of(new LeagueDraftPick().pick(1).round(1).teamId(1).playerId(4697382L).keeper(false))));

        mockMvc.perform(get("/api/v1/espn/leagues/123/draft")
                        .header("Authorization", "Bearer " + jwtTokenValidator.generateToken(USER_ID, "a@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.teams[0].id").value("espn.l.123.t.1"))
                .andExpect(jsonPath("$.teams[0].mine").value(true))
                .andExpect(jsonPath("$.picks[0].teamId").value("espn.l.123.t.1"))
                .andExpect(jsonPath("$.picks[0].playerId").value(4697382));
    }

    @Test
    void reportsOnlyTheEspnFeatureAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leagueDraftSync").value(false))
                .andExpect(jsonPath("$.espnLeagueDraftSync").value(true));
    }

    @Test
    void refusesASignedOutRequest() throws Exception {
        mockMvc.perform(get("/api/v1/espn/leagues/123/draft"))
                .andExpect(status().isUnauthorized());
    }
}
