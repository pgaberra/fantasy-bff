package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.security.JwtTokenValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Left unset, the rest-of-season preset is off even where the AI projection is served: its preview
 * is a 404 without asking projection-service, and it is reported off so the web does not offer it.
 * Whether a season is under way is still answered, since that is the AI projection's to say, not
 * the preset's: Team Power Rankings names its option by it before any ranking is read.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RestOfSeasonPresetDisabledTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private ProjectionServiceClient projectionServiceClient;

    @Test
    void theSeasonStatusIsStillServed() throws Exception {
        when(projectionServiceClient.restOfSeasonSkaters(anyInt())).thenReturn(List.of());
        when(projectionServiceClient.restOfSeasonGoalies(anyInt())).thenReturn(List.of());
        String bearer = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");
        mockMvc.perform(get("/api/v1/projection-model/rest-of-season/status").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    /** Its preview is off with it, so the create page has nothing to draw under a card it does not show. */
    @Test
    void theBoardAndSeedAreNotServedAndProjectionServiceIsNotAsked() throws Exception {
        String bearer = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");
        mockMvc.perform(get("/api/v1/projection-model/board?source=rest_of_season").header("Authorization", bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projection-model/seed?source=rest_of_season&skaterLimit=5")
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
        verifyNoInteractions(projectionServiceClient);
    }

    @Test
    void theFeatureIsReportedOff() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restOfSeasonPreset").value(false));
    }
}
