package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The planner on and its own-team view left unset: the view is refused, not merely hidden, and
 * the platform is never asked for the roster.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "streamer-planner.enabled=true")
class StreamerPlannerMyTeamDisabledTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private YahooServiceClient yahooServiceClient;

    @Test
    void theOwnTeamViewIsNotServed() throws Exception {
        String bearer = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");
        mockMvc.perform(get("/api/v1/streamer-planner/my-team?platform=YAHOO&leagueId=465.l.9")
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
        verifyNoInteractions(yahooServiceClient);
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(jsonPath("$.streamerPlanner").value(true))
                .andExpect(jsonPath("$.streamerPlannerMyTeam").value(false));
    }
}
