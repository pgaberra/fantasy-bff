package com.fantasy.bff.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DRAFT_ANALYSIS_ENABLED unset is the default in every environment: its route is a 404 that asks
 * nothing downstream, and the feature is reported off.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DraftAnalysisDisabledTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private ProjectionServiceClient projectionServiceClient;
    @MockitoBean private YahooServiceClient yahooServiceClient;

    @Test
    void theAnalysisIsNotServedAndNothingDownstreamIsAsked() throws Exception {
        String bearer = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");
        mockMvc.perform(get("/api/v1/draft-analysis/yahoo/465.l.9").header("Authorization", bearer))
                .andExpect(status().isNotFound());
        verifyNoInteractions(projectionServiceClient, yahooServiceClient);
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/draft-analysis/yahoo/465.l.9"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theFeatureIsReportedOff() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftAnalysis").value(false));
    }
}
