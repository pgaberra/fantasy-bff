package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.PlayerServiceClient;
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
}
