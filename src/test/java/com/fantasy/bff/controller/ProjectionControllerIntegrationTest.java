package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.ImportProjectionRequest;
import com.fantasy.bff.generated.db.model.ProjectionOrigin;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private static final UUID DRAFT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** What the draft page sends: its own league and setup, with the rows left to the server. */
    private static final String START_DRAFT_BODY = """
            {
              "name": "My mock",
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
                "players": []
              }
            }
            """;

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
     * The cap comes from the pinned spec rather than from an annotation written here — the
     * generator emits {@code @Size} for {@code maxItems} — so it is worth a test that this
     * boundary actually rejects, instead of trusting that db-service will.
     */
    @Test
    void create_withMorePlayerRowsThanAnyLeagueHas_returns400AndNeverReachesDownstream()
            throws Exception {
        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPlayerRows(2001)))
                .andExpect(status().isBadRequest());

        verify(databaseServiceClient, never()).createProjection(any(), any());
    }

    @Test
    void create_withAsManyRowsAsTheLargestPlayerPoolHas_isAccepted() throws Exception {
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(
                new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027)
                        .id(PROJECTION_ID.toString()).name("My league"));

        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPlayerRows(2000)))
                .andExpect(status().isCreated());
    }

    /** Built rather than patched into {@link #VALID_BODY}, so it does not depend on that layout. */
    private static String bodyWithPlayerRows(int rows) {
        String players = IntStream.rangeClosed(1, rows)
                .mapToObj(id -> ("{\"playerId\":%d,\"type\":\"skater\","
                        + "\"stats\":{\"utility\":{\"gp\":82},\"scoring\":{\"goals\":64}}}").formatted(id))
                .collect(Collectors.joining(","));
        return ("{\"name\":\"My league\",\"data\":{"
                + "\"settings\":{\"scoringType\":\"points\",\"statWeights\":{\"goals\":4.5},"
                + "\"activeScoringColumns\":[\"goals\"],\"activeUtilityColumns\":[\"gp\"],"
                + "\"scaleSettings\":{},\"decimalSettings\":{\"goals\":0},\"useDefaultDecimals\":true},"
                + "\"players\":[%s]}}").formatted(players);
    }

    /**
     * The point of {@code source}: the client sends settings only (~1 kB) instead of every
     * player in the league (~0.5 MB), and the server fills the rows in from its read model.
     */
    @Test
    void create_withSource_fillsPlayersServerSide() throws Exception {
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of(new SkaterResponse(
                1, "Connor McDavid", "EDM", "https://example.test/1.png", 97, Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(82, 1320),
                        new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1, 23, 38, 61,
                                8, 2, 348, 18.4, 812, 623, 42, 28, 0, 1408, 92400)))));
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
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
    void create_withDraftKind_forwardsTheKindDownstream() throws Exception {
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(
                new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027).id(PROJECTION_ID.toString()).name("Last Season's Stats"));

        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SOURCED_BODY.replace("\"source\": \"default\",",
                                "\"source\": \"default\", \"kind\": \"draft\",")))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateProjectionRequest> sent = ArgumentCaptor.forClass(CreateProjectionRequest.class);
        verify(databaseServiceClient).createProjection(eq(USER_ID), sent.capture());
        assertThat(sent.getValue().getKind()).isEqualTo(CreateProjectionRequest.KindEnum.DRAFT);
    }

    /**
     * The name is the heading the draft board shows, so a client must not be able to make a
     * draft claim it was drafted against something it wasn't.
     */
    @Test
    void create_asAPresetDraft_isNamedByTheServerNotTheCaller() throws Exception {
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(
                new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027).id(PROJECTION_ID.toString()).name("Last Season's Stats"));

        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SOURCED_BODY
                                .replace("\"name\": \"My league\",", "\"name\": \"Totally legit ranking\",")
                                .replace("\"source\": \"default\",",
                                        "\"source\": \"default\", \"kind\": \"draft\",")))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateProjectionRequest> sent = ArgumentCaptor.forClass(CreateProjectionRequest.class);
        verify(databaseServiceClient).createProjection(eq(USER_ID), sent.capture());
        assertThat(sent.getValue().getName()).isEqualTo("Last Season's Stats");
    }

    /** A preset whose rows came from the caller would not be the preset. */
    @Test
    void create_asADraftWithoutAPresetSource_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/projections")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"name\": \"My league\",",
                                "\"name\": \"My league\", \"kind\": \"draft\",")))
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

    @Test
    void importFromShare_copiesTheBoardAndReportsWhoseItWas() throws Exception {
        when(databaseServiceClient.importProjection(eq(USER_ID), any())).thenReturn(
                new ProjectionResponse()
                        .season(ProjectionResponse.SeasonEnum._20262027)
                        .id(PROJECTION_ID.toString())
                        .name("Their league")
                        .kind(ProjectionResponse.KindEnum.IMPORTED)
                        .origin(new ProjectionOrigin()
                                .shareToken("s0mErAnd0mT0k3nV4lu3ab")
                                .authorUsername("alex")));

        mockMvc.perform(post("/api/v1/projections/imports")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"s0mErAnd0mT0k3nV4lu3ab\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("imported"))
                .andExpect(jsonPath("$.origin.authorUsername").value("alex"))
                .andExpect(jsonPath("$.origin.shareToken").value("s0mErAnd0mT0k3nV4lu3ab"));

        ArgumentCaptor<ImportProjectionRequest> sent =
                ArgumentCaptor.forClass(ImportProjectionRequest.class);
        verify(databaseServiceClient).importProjection(eq(USER_ID), sent.capture());
        assertThat(sent.getValue().getToken()).isEqualTo("s0mErAnd0mT0k3nV4lu3ab");
    }

    /** The page sends the stamp it read, so a board changed since can be refused downstream. */
    @Test
    void importFromShare_passesOnTheStampThePageRead() throws Exception {
        when(databaseServiceClient.importProjection(eq(USER_ID), any())).thenReturn(
                new ProjectionResponse()
                        .season(ProjectionResponse.SeasonEnum._20262027)
                        .id(PROJECTION_ID.toString())
                        .name("Their league")
                        .kind(ProjectionResponse.KindEnum.IMPORTED));

        mockMvc.perform(post("/api/v1/projections/imports")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"s0mErAnd0mT0k3nV4lu3ab\", "
                                + "\"seenUpdatedAt\": \"2026-09-18T08:00:00.123456Z\" }"))
                .andExpect(status().isCreated());

        ArgumentCaptor<ImportProjectionRequest> sent =
                ArgumentCaptor.forClass(ImportProjectionRequest.class);
        verify(databaseServiceClient).importProjection(eq(USER_ID), sent.capture());
        assertThat(sent.getValue().getSeenUpdatedAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-18T08:00:00.123456Z"));
    }

    /**
     * A board changed since it was read reaches the page as 412, not as the 502 every other
     * downstream status becomes: the page reloads the board on it, where a 502 it would retry.
     */
    @Test
    void importFromShare_ofABoardChangedSinceItWasRead_returns412() throws Exception {
        when(databaseServiceClient.importProjection(eq(USER_ID), any())).thenThrow(
                new RestClientResponseException("Precondition Failed", HttpStatus.PRECONDITION_FAILED,
                        "Precondition Failed", null, null, null));

        mockMvc.perform(post("/api/v1/projections/imports")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"s0mErAnd0mT0k3nV4lu3ab\", "
                                + "\"seenUpdatedAt\": \"2026-09-18T08:00:00Z\" }"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
    }

    @Test
    void importFromShare_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/projections/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"s0mErAnd0mT0k3nV4lu3ab\" }"))
                .andExpect(status().isUnauthorized());

        verify(databaseServiceClient, never()).importProjection(any(), any());
    }

    @Test
    void importFromShare_withBlankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/projections/imports")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \" \" }"))
                .andExpect(status().isBadRequest());

        verify(databaseServiceClient, never()).importProjection(any(), any());
    }
    /**
     * The whole of what was asked for: a board can be drafted against again and again. The rows
     * are copied downstream, so nothing of the board travels in either direction.
     */
    @Test
    void startDraft_copiesTheBoardDownstreamAndReturnsTheDraft() throws Exception {
        when(databaseServiceClient.startDraft(eq(USER_ID), eq(PROJECTION_ID), any())).thenReturn(
                new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027)
                        .id(DRAFT_ID.toString()).name("My league (2)")
                        .kind(ProjectionResponse.KindEnum.DRAFT)
                        .sourceProjectionId(PROJECTION_ID.toString())
                        .autoNamed(true));

        mockMvc.perform(post("/api/v1/projections/{id}/drafts", PROJECTION_ID)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(START_DRAFT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("draft"))
                .andExpect(jsonPath("$.name").value("My league (2)"))
                .andExpect(jsonPath("$.sourceProjectionId").value(PROJECTION_ID.toString()))
                .andExpect(jsonPath("$.autoNamed").value(true));

        ArgumentCaptor<com.fantasy.bff.generated.db.model.StartDraftRequest> sent =
                ArgumentCaptor.forClass(com.fantasy.bff.generated.db.model.StartDraftRequest.class);
        verify(databaseServiceClient).startDraft(eq(USER_ID), eq(PROJECTION_ID), sent.capture());
        assertThat(sent.getValue().getData().getPlayers()).isEmpty();
    }

    @Test
    void startDraft_withoutAName_leavesTheNamingToTheServer() throws Exception {
        when(databaseServiceClient.startDraft(eq(USER_ID), eq(PROJECTION_ID), any())).thenReturn(
                new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027)
                        .id(DRAFT_ID.toString()).name("My league")
                        .kind(ProjectionResponse.KindEnum.DRAFT).autoNamed(true));

        mockMvc.perform(post("/api/v1/projections/{id}/drafts", PROJECTION_ID)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(START_DRAFT_BODY.replace("\"name\": \"My mock\",", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My league"));

        ArgumentCaptor<com.fantasy.bff.generated.db.model.StartDraftRequest> sent =
                ArgumentCaptor.forClass(com.fantasy.bff.generated.db.model.StartDraftRequest.class);
        verify(databaseServiceClient).startDraft(eq(USER_ID), eq(PROJECTION_ID), sent.capture());
        assertThat(sent.getValue().getName()).isNull();
    }

    @Test
    void startDraft_needsAToken() throws Exception {
        mockMvc.perform(post("/api/v1/projections/{id}/drafts", PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(START_DRAFT_BODY))
                .andExpect(status().isUnauthorized());

        verify(databaseServiceClient, never()).startDraft(any(), any(), any());
    }

    @Test
    void rename_forwardsTheNameAndReturnsWhatWasSaved() throws Exception {
        when(databaseServiceClient.renameProjection(eq(USER_ID), eq(DRAFT_ID), any())).thenReturn(
                summary("Mock #3", ProjectionSummaryResponse.KindEnum.DRAFT));

        mockMvc.perform(put("/api/v1/projections/{id}/name", DRAFT_ID)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mock #3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mock #3"));

        ArgumentCaptor<com.fantasy.bff.generated.db.model.RenameProjectionRequest> sent =
                ArgumentCaptor.forClass(com.fantasy.bff.generated.db.model.RenameProjectionRequest.class);
        verify(databaseServiceClient).renameProjection(eq(USER_ID), eq(DRAFT_ID), sent.capture());
        assertThat(sent.getValue().getName()).isEqualTo("Mock #3");
        assertThat(sent.getValue().getDerived()).isNull();
    }

    /** A league sync's rename carries the flag that lets the server decline it. */
    @Test
    void rename_passesTheDerivedFlagDownstream() throws Exception {
        when(databaseServiceClient.renameProjection(eq(USER_ID), eq(DRAFT_ID), any())).thenReturn(
                summary("Beer League", ProjectionSummaryResponse.KindEnum.DRAFT));

        mockMvc.perform(put("/api/v1/projections/{id}/name", DRAFT_ID)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beer League\", \"derived\": true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<com.fantasy.bff.generated.db.model.RenameProjectionRequest> sent =
                ArgumentCaptor.forClass(com.fantasy.bff.generated.db.model.RenameProjectionRequest.class);
        verify(databaseServiceClient).renameProjection(eq(USER_ID), eq(DRAFT_ID), sent.capture());
        assertThat(sent.getValue().getDerived()).isTrue();
    }

    @Test
    void rename_rejectsABlankName() throws Exception {
        mockMvc.perform(put("/api/v1/projections/{id}/name", DRAFT_ID)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"  \"}"))
                .andExpect(status().isBadRequest());

        verify(databaseServiceClient, never()).renameProjection(any(), any(), any());
    }

    private static ProjectionSummaryResponse summary(String name,
                                                     ProjectionSummaryResponse.KindEnum kind) {
        return new ProjectionSummaryResponse()
                .id(DRAFT_ID.toString())
                .name(name)
                .kind(kind)
                .season(ProjectionSummaryResponse.SeasonEnum._20262027)
                .createdAt(OffsetDateTime.parse("2026-09-19T10:00:00Z"))
                .updatedAt(OffsetDateTime.parse("2026-09-19T10:00:00Z"))
                .draftStatus(ProjectionSummaryResponse.DraftStatusEnum.IN_PROGRESS)
                .autoNamed(false);
    }
}
