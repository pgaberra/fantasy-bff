package com.fantasy.bff.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Left unset, the available-players filter is off: refused at the endpoint rather than merely
 * hidden by the web, and reported off so the web does not offer it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "players.source=yahoo")
class LeagueRosteredPlayersDisabledTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private YahooServiceClient yahooServiceClient;
    @MockitoBean private EspnServiceClient espnServiceClient;

    @Test
    void theRosterIsNotServedAndNoPlatformIsAsked() throws Exception {
        String bearer = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");
        mockMvc.perform(get("/api/v1/leagues/rostered-players?platform=YAHOO&leagueId=465.l.9")
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/leagues/rostered-players?platform=ESPN&leagueId=123")
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
        verifyNoInteractions(yahooServiceClient, espnServiceClient);
    }

    @Test
    void theFeatureIsReportedOff() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whosHotAvailableFilter").value(false));
    }
}
