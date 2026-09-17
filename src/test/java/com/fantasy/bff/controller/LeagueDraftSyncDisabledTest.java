package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.YahooServiceClient;
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
 * Left unset, following a league's draft is off: refused at the endpoint rather than merely hidden
 * by the web, and reported off so the web does not offer it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeagueDraftSyncDisabledTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private YahooServiceClient yahooServiceClient;

    @Test
    void theDraftIsNotServedAndYahooIsNotAsked() throws Exception {
        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.9/draft")
                        .header("Authorization", "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com")))
                .andExpect(status().isNotFound());
        verifyNoInteractions(yahooServiceClient);
    }

    @Test
    void theFeatureIsReportedOff() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leagueDraftSync").value(false));
    }
}
