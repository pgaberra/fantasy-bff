package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The model endpoints serve the projection engine's raw output, and whether that is free to
 * every signed-in user is still an open decision (fantasy-bff#105). Until it is made, a
 * deployment that has not set PROJECTION_MODEL_ENABLED must refuse them outright — not merely
 * fail because the downstream service happens to be unreachable, which would leave the door
 * open the moment anyone starts that service.
 *
 * <p>No property is set here on purpose: this asserts the shipped default.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectionModelDisabledTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private ProjectionServiceClient projectionServiceClient;

    @MockitoBean
    private PlayerServiceClient playerServiceClient;

    private String token() {
        return jwtTokenValidator.generateToken("user-1", "test@example.com");
    }

    @Test
    void seed_whenDisabled_isForbiddenEvenForASignedInUser() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/seed").header("Authorization", "Bearer " + token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void skaterSplits_whenDisabled_areForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters").header("Authorization", "Bearer " + token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void goalieSplits_whenDisabled_areForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/goalies").header("Authorization", "Bearer " + token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenDisabled_theProjectionServiceIsNeverCalled() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/seed").header("Authorization", "Bearer " + token()));

        verifyNoInteractions(projectionServiceClient);
    }

    /**
     * The second door to the model's lines. Closing the prefix used to leave it open, so an
     * environment that denied /seed still filled a projection or a preset draft from the model.
     */
    @Test
    void modelSeededProjection_whenDisabled_isRefused() throws Exception {
        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "My board",
                                  "source": "model",
                                  "data": {
                                    "settings": {
                                      "scoringType": "points",
                                      "statWeights": { "goals": 4.5 },
                                      "activeScoringColumns": ["goals"],
                                      "activeUtilityColumns": ["gp"],
                                      "scaleSettings": {},
                                      "decimalSettings": { "goals": 0 },
                                      "useDefaultDecimals": true
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projectionServiceClient);
    }

    /** What the web reads to decide whether to offer the preset at all, signed in or not. */
    @Test
    void features_whenDisabled_reportTheAiProjectionUnavailable() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiProjection").value(false));
    }
}
