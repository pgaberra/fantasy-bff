package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Left unset, the streamer planner is off: refused at the endpoints rather than merely hidden by
 * the web, and reported off so the web does not offer it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StreamerPlannerDisabledTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private ProjectionServiceClient projectionServiceClient;

    @Test
    void thePlannerIsNotServedAndProjectionServiceIsNotAsked() throws Exception {
        String bearer = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");
        mockMvc.perform(get("/api/v1/streamer-planner/weeks").header("Authorization", bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/streamer-planner/teams?start=2026-10-12&end=2026-10-18")
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
        verifyNoInteractions(projectionServiceClient);
    }

    @Test
    void theFeatureIsReportedOff() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streamerPlanner").value(false));
    }
}
