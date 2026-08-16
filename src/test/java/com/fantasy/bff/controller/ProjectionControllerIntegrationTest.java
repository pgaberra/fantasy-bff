package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSummaryResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @MockitoBean
    private PlayerServiceClient playerServiceClient;

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

    /** A create that leaves the player rows to the server: settings only, no players. */
    private static final String SOURCED_BODY = """
            {
              "name": "My league",
              "source": "default",
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
                new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027).id(PROJECTION_ID.toString()).name("My league"));

        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My league"));
    }

    /**
     * The point of {@code source}: the client sends settings only (~1 kB) instead of every
     * player in the league (~0.5 MB), and the server fills the rows in from its read model.
     */
    @Test
    void create_withSource_fillsPlayersServerSide() throws Exception {
        when(playerServiceClient.getSkaters()).thenReturn(List.of(new SkaterResponse(
                1, "Connor McDavid", "EDM", "https://example.test/1.png", 97, Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(82, 1320),
                        new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1, 23, 38, 61,
                                8, 2, 348, 18.4, 812, 623, 42, 28, 0, 1408, 92400)))));
        when(playerServiceClient.getGoalies()).thenReturn(List.of());
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(
                new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027).id(PROJECTION_ID.toString()).name("My league"));

        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SOURCED_BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateProjectionRequest> sent = ArgumentCaptor.forClass(CreateProjectionRequest.class);
        verify(databaseServiceClient).createProjection(eq(USER_ID), sent.capture());
        assertThat(sent.getValue().getData().getPlayers()).hasSize(1);
        assertThat(sent.getValue().getData().getPlayers().getFirst().getStats().getScoring())
                .containsEntry("goals", 64.0);
    }

    /**
     * A draft started from a preset has no projection behind it, so it is stored as one of its
     * own kind — kept out of the projections the user made.
     */
    @Test
    void create_withPresetDraftKind_forwardsTheKindDownstream() throws Exception {
        when(playerServiceClient.getSkaters()).thenReturn(List.of());
        when(playerServiceClient.getGoalies()).thenReturn(List.of());
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(
                new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027).id(PROJECTION_ID.toString()).name("Last Season's Stats"));

        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SOURCED_BODY.replace("\"source\": \"default\",",
                                "\"source\": \"default\", \"kind\": \"preset_draft\",")))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateProjectionRequest> sent = ArgumentCaptor.forClass(CreateProjectionRequest.class);
        verify(databaseServiceClient).createProjection(eq(USER_ID), sent.capture());
        assertThat(sent.getValue().getKind()).isEqualTo(CreateProjectionRequest.KindEnum.PRESET_DRAFT);
    }

    /**
     * The name is the heading the draft board shows, so a client must not be able to make a
     * draft claim it was drafted against something it wasn't.
     */
    @Test
    void create_asPresetDraft_isNamedByTheServerNotTheCaller() throws Exception {
        when(playerServiceClient.getSkaters()).thenReturn(List.of());
        when(playerServiceClient.getGoalies()).thenReturn(List.of());
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(
                new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027).id(PROJECTION_ID.toString()).name("Last Season's Stats"));

        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SOURCED_BODY
                                .replace("\"name\": \"My league\",", "\"name\": \"Totally legit ranking\",")
                                .replace("\"source\": \"default\",",
                                        "\"source\": \"default\", \"kind\": \"preset_draft\",")))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateProjectionRequest> sent = ArgumentCaptor.forClass(CreateProjectionRequest.class);
        verify(databaseServiceClient).createProjection(eq(USER_ID), sent.capture());
        assertThat(sent.getValue().getName()).isEqualTo("Last Season's Stats");
    }

    /** A preset whose rows came from the caller would not be the preset. */
    @Test
    void create_asPresetDraftWithoutTheDefaultSource_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"name\": \"My league\",",
                                "\"name\": \"My league\", \"kind\": \"preset_draft\",")))
                .andExpect(status().isBadRequest());

        verify(databaseServiceClient, never()).createProjection(any(), any());
    }

    @Test
    void create_withoutKind_defaultsToTheUsersOwnProjection() throws Exception {
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(
                new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027).id(PROJECTION_ID.toString()).name("My league"));

        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateProjectionRequest> sent = ArgumentCaptor.forClass(CreateProjectionRequest.class);
        verify(databaseServiceClient).createProjection(eq(USER_ID), sent.capture());
        assertThat(sent.getValue().getKind()).isEqualTo(CreateProjectionRequest.KindEnum.PROJECTION);
    }

    @Test
    void create_withNeitherSourceNorPlayers_isRejected() throws Exception {
        String body = SOURCED_BODY.replace("\"source\": \"default\",", "");

        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(databaseServiceClient, never()).createProjection(any(), any());
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
