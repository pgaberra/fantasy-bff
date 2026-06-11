package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSummaryResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectionControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private DatabaseServiceClient databaseServiceClient;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String VALID_BODY = """
            {
              "name": "My league",
              "data": {
                "settings": {
                  "scoringType": "points",
                  "statWeights": { "goals": 4.5 },
                  "activeScoringColumns": ["goals"],
                  "activeUtilityColumns": ["gp"],
                  "scaleSettings": {},
                  "decimalSettings": { "goals": 0 },
                  "useDefaultDecimals": true
                },
                "players": [
                  { "playerId": 1, "type": "skater",
                    "stats": { "utility": { "gp": 82 }, "scoring": { "goals": 64 } } }
                ]
              }
            }
            """;

    private String token() {
        return jwtTokenValidator.generateToken(USER_ID.toString(), "owner@example.com");
    }

    @Test
    void list_withValidToken_returnsProjections() throws Exception {
        when(databaseServiceClient.listProjections(USER_ID)).thenReturn(List.of(
                new ProjectionSummaryResponse().id(PROJECTION_ID.toString()).name("My league")));

        mockMvc.perform(get("/api/v1/projections").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My league"));
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/projections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withValidToken_returns201() throws Exception {
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(
                new ProjectionResponse().id(PROJECTION_ID.toString()).name("My league"));

        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My league"));
    }

    @Test
    void get_whenDownstreamReturns404_relays404() throws Exception {
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenThrow(
                new RestClientResponseException("Not Found", HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        mockMvc.perform(get("/api/v1/projections/{id}", PROJECTION_ID)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_withValidToken_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/projections/{id}", PROJECTION_ID)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isNoContent());
    }
}
