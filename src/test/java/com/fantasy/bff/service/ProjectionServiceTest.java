package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.CreateProjectionRequest;
import com.fantasy.bff.dto.request.ProjectionKind;
import com.fantasy.bff.dto.request.ProjectionSource;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.ProjectionData;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.fantasy.bff.service.ProjectionPoolReconciler.Reconciliation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A projection covers every player in the league, so having the client upload rows it just
 * downloaded made creating one depend on a ~0.5 MB request that was failing in production
 * (JAVA-SPRING-BOOT-J). The server can derive both starting points itself.
 */
@ExtendWith(MockitoExtension.class)
class ProjectionServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PROJECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final int SEASON = 2026;
    private static final String MODEL_VERSION = "marcel-v3";

    @Mock
    private DatabaseServiceClient databaseServiceClient;

    @Mock
    private PlayerService playerService;

    @Mock
    private PlayerPoolSource playerPool;

    @Mock
    private ProjectionPoolReconciler reconciler;

    @Mock
    private ProjectionSeedService seedService;

    @Captor
    private ArgumentCaptor<com.fantasy.bff.generated.db.model.CreateProjectionRequest> sentRequest;

    private ProjectionService projectionService;

    @BeforeEach
    void setUp() {
        lenient().when(playerPool.playerIdSpace()).thenReturn(PlayerIdSpace.YAHOO);
        projectionService = new ProjectionService(
                databaseServiceClient,
                new PlayerPoolRows(playerService, JsonMapper.builder().build()),
                playerPool,
                reconciler,
                seedService,
                SEASON,
                MODEL_VERSION);
    }

    @Test
    void withDefaultSource_fillsPlayersFromTheReadModelKeepingTheirStats() {
        givenOneSkaterAndOneGoalie();
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.DEFAULT));

        List<PlayerProjection> players = capturedPlayers();
        assertThat(players).hasSize(2);
        assertThat(players.getFirst().getPlayerId()).isEqualTo(1);
        assertThat(players.getFirst().getType()).isEqualTo(PlayerProjection.TypeEnum.SKATER);
        assertThat(players.getLast().getType()).isEqualTo(PlayerProjection.TypeEnum.GOALIE);

        PlayerStats skaterStats = players.getFirst().getStats();
        assertThat(skaterStats.getUtility()).containsEntry("gp", 82.0).containsEntry("toiPerGame", 1320.0);
        assertThat(skaterStats.getScoring()).containsEntry("goals", 64.0).containsEntry("assists", 89.0);
        assertThat(players.getLast().getStats().getScoring()).containsEntry("w", 36.0);
    }

    // The stat keys must match what the same records serialise to on /api/v1/players/*, since
    // that is what the client's projections are keyed by.
    @Test
    void withDefaultSource_usesTheSameStatKeysAsThePlayerEndpoints() {
        givenOneSkaterAndOneGoalie();
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.DEFAULT));

        assertThat(capturedPlayers().getFirst().getStats().getScoring())
                .containsKeys("goals", "assists", "points", "plusMinus", "pim", "ppg", "ppa", "shg", "sha",
                        "gwg", "sog", "shPct", "fw", "fl", "hits", "blocks");
    }

    @Test
    void withBlankSource_keepsEveryPlayerButZeroesTheStats() {
        givenOneSkaterAndOneGoalie();
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.BLANK));

        List<PlayerProjection> players = capturedPlayers();
        assertThat(players).hasSize(2);
        assertThat(players.getFirst().getStats().getScoring().values()).containsOnly(0.0);
        assertThat(players.getFirst().getStats().getUtility().values()).containsOnly(0.0);
        assertThat(players.getLast().getStats().getScoring().values()).containsOnly(0.0);
    }

    /**
     * What the rows started as is the answer to what a player who joins the pool later gets, so
     * it is stored with them rather than left as a choice only the create call ever saw.
     */
    @Test
    void recordsWhichStartingPointTheRowsCameFrom() {
        givenOneSkaterAndOneGoalie();
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.DEFAULT));
        assertThat(capturedSettings().getPlayerBasis()).isEqualTo(PlayerBasisEnum.LAST_SEASON);
    }

    @Test
    void recordsABlankProjectionAsStartedFromScratch() {
        givenOneSkaterAndOneGoalie();
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.BLANK));
        assertThat(capturedSettings().getPlayerBasis()).isEqualTo(PlayerBasisEnum.BLANK);
    }

    /**
     * Reading is where a projection meets the pool as it is today, so a read that had to square
     * the two saves the result — otherwise every later read would redo the same work — and says
     * how much moved, which is the only chance the client gets to tell the user.
     */
    @Test
    void aReadThatSquaredTheRowsWithThePoolSavesThemAndSaysWhatMoved() {
        ProjectionResponse stored = storedProjection();
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(stored);
        when(reconciler.reconcile(stored.getData())).thenReturn(Optional.of(new Reconciliation(12)));
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(stored);

        var response = projectionService.get(USER_ID, PROJECTION_ID);

        assertThat(response.poolReconciliation().added()).isEqualTo(12);
        ArgumentCaptor<UpdateProjectionRequest> saved = ArgumentCaptor.forClass(UpdateProjectionRequest.class);
        verify(databaseServiceClient).updateProjection(eq(USER_ID), eq(PROJECTION_ID), saved.capture());
        assertThat(saved.getValue().getData().getPlayers()).isEqualTo(stored.getData().getPlayers());
    }

    /** Saving the reconciliation is our business, not the caller's — losing it costs them nothing. */
    @Test
    void servesTheReconciledRowsEvenIfSavingThemFails() {
        ProjectionResponse stored = storedProjection();
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(stored);
        when(reconciler.reconcile(stored.getData())).thenReturn(Optional.of(new Reconciliation(12)));
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenThrow(new IllegalStateException("db-service is down"));

        var response = projectionService.get(USER_ID, PROJECTION_ID);

        assertThat(response.data()).isEqualTo(stored.getData());
        assertThat(response.poolReconciliation().added()).isEqualTo(12);
    }

    @Test
    void aReadWithNothingToSquareTouchesNothing() {
        ProjectionResponse stored = storedProjection();
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(stored);
        when(reconciler.reconcile(stored.getData())).thenReturn(Optional.empty());

        var response = projectionService.get(USER_ID, PROJECTION_ID);

        assertThat(response.poolReconciliation()).isNull();
        verify(databaseServiceClient, never()).updateProjection(any(), any(), any());
    }

    @Test
    void withoutSource_passesTheClientsOwnPlayersThroughUntouched() {
        PlayerProjection own = new PlayerProjection()
                .playerId(7)
                .type(PlayerProjection.TypeEnum.SKATER)
                .stats(new PlayerStats().utility(Map.of("gp", 12.0)).scoring(Map.of("goals", 3.0)));
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(dataWith(own), null));

        assertThat(capturedPlayers()).containsExactly(own);
        verifyNoInteractions(playerService);
    }

    // Silently dropping the rows a client did send would lose a copied or demo projection.
    @Test
    void withSourceAndPlayers_isRejectedRatherThanChoosingOne() {
        CreateProjectionRequest request = request(dataWith(new PlayerProjection().playerId(7)),
                ProjectionSource.DEFAULT);

        assertThatThrownBy(() -> projectionService.create(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");

        verify(databaseServiceClient, never()).createProjection(any(), any());
    }

    @Test
    void withoutSourceAndWithoutPlayers_isRejected() {
        CreateProjectionRequest request = request(emptyData(), null);

        assertThatThrownBy(() -> projectionService.create(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");

        verify(databaseServiceClient, never()).createProjection(any(), any());
    }

    private void givenOneSkaterAndOneGoalie() {
        when(playerService.getSkaters()).thenReturn(List.of(new SkaterResponse(
                1, "Connor McDavid", "EDM", "https://example.test/1.png", 97, Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(82, 1320),
                        new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1, 23, 38, 61,
                                8, 2, 348, 18.4, 812, 623, 42, 28, 0, 1408, 92400)))));
        when(playerService.getGoalies()).thenReturn(List.of(new GoalieResponse(
                101, "Igor Shesterkin", "NYR", "https://example.test/101.png", 31,
                new GoalieResponse.Stats(
                        new GoalieResponse.UtilityStats(58),
                        new GoalieResponse.ScoringStats(58, 36, 17, 4, 3, 1720, 1565, 155, 2.67, 0.910,
                                0.632, 209000)))));
    }

    private List<PlayerProjection> capturedPlayers() {
        return capturedData().getPlayers();
    }

    private ProjectionSettings capturedSettings() {
        return capturedData().getProjectionSettings();
    }

    private ProjectionData capturedData() {
        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        return sentRequest.getValue().getData();
    }

    /** db-service always stamps a season; the response type takes it as given. */
    private static ProjectionResponse created() {
        return new ProjectionResponse().season(ProjectionResponse.SeasonEnum._20262027);
    }

    private static ProjectionResponse storedProjection() {
        return new ProjectionResponse()
                .id(PROJECTION_ID.toString())
                .name("My Projection")
                .season(ProjectionResponse.SeasonEnum._20262027)
                .data(dataWith(new PlayerProjection()
                        .playerId(7)
                        .type(PlayerProjection.TypeEnum.SKATER)
                        .stats(new PlayerStats().utility(Map.of("gp", 12.0)).scoring(Map.of("goals", 3.0)))));
    }

    private static CreateProjectionRequest request(ProjectionData data, ProjectionSource source) {
        return request(data, source, null);
    }

    private static CreateProjectionRequest request(ProjectionData data, ProjectionSource source,
                                                   ProjectionKind kind) {
        return new CreateProjectionRequest("My Projection", kind, data, source);
    }

    private static ProjectionData emptyData() {
        return new ProjectionData().projectionSettings(new ProjectionSettings());
    }

    private static ProjectionData dataWith(PlayerProjection player) {
        return new ProjectionData().projectionSettings(new ProjectionSettings()).players(List.of(player));
    }

    /**
     * The stamp has to follow whichever pool is wired in. Stored as Yahoo's while the rows are
     * ESPN's, a later id remap picks the projection up and translates ids that were never in
     * the space it assumed.
     */
    @Test
    void stampsTheCreateWithTheIdSpaceOfThePoolThatIsWiredIn() {
        when(playerPool.playerIdSpace()).thenReturn(PlayerIdSpace.ESPN);
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(dataWith(new PlayerProjection().playerId(7)), null));

        ArgumentCaptor<com.fantasy.bff.generated.db.model.CreateProjectionRequest> sent =
                ArgumentCaptor.forClass(com.fantasy.bff.generated.db.model.CreateProjectionRequest.class);
        verify(databaseServiceClient).createProjection(eq(USER_ID), sent.capture());
        assertThat(sent.getValue().getPlayerIdSpace()).isEqualTo(
                com.fantasy.bff.generated.db.model.CreateProjectionRequest.PlayerIdSpaceEnum.ESPN);
    }

    @Test
    @DisplayName("model source fills the rows from the projection model, not the read model")
    void withModelSource_fillsPlayersFromTheModel() {
        PlayerProjection projected = new PlayerProjection();
        projected.setPlayerId(4242);
        projected.setType(PlayerProjection.TypeEnum.SKATER);
        when(seedService.seed(SEASON, MODEL_VERSION))
                .thenReturn(new ProjectionSeedService.Seed(List.of(projected), 1, 0, 0, 0, 0));
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.MODEL));

        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        assertThat(sentRequest.getValue().getData().getPlayers())
                .extracting(PlayerProjection::getPlayerId)
                .containsExactly(4242);
        // The read model must not be consulted at all for this source.
        verifyNoInteractions(playerService);
    }

    @Test
    @DisplayName("a model-seeded projection records no player basis rather than a wrong one")
    void withModelSource_leavesPlayerBasisUnset() {
        // playerBasis answers "what should a player who joins the pool later be seeded with".
        // The reconciler reads the player pool, not the model, so it cannot answer that here —
        // and storing last_season would be a lie the next reconciliation acts on.
        when(seedService.seed(SEASON, MODEL_VERSION))
                .thenReturn(new ProjectionSeedService.Seed(List.of(), 0, 0, 0, 0, 0));
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.MODEL));

        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        assertThat(sentRequest.getValue().getData().getProjectionSettings().getPlayerBasis()).isNull();
    }

    @Test
    @DisplayName("a preset draft may be started from the model, and is named for it")
    void presetDraftFromModel_isNamedForThePreset() {
        when(seedService.seed(SEASON, MODEL_VERSION))
                .thenReturn(new ProjectionSeedService.Seed(List.of(), 0, 0, 0, 0, 0));
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(
                USER_ID,
                request(emptyData(), ProjectionSource.MODEL, ProjectionKind.PRESET_DRAFT));

        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        // The board heading comes from here, so it must name the preset actually drafted against.
        assertThat(sentRequest.getValue().getName()).isEqualTo("AI Projection");
    }

    @Test
    @DisplayName("a preset draft still cannot be started from a blank pool")
    void presetDraftFromBlank_isRejected() {
        assertThatThrownBy(() -> projectionService.create(
                        USER_ID,
                        request(emptyData(), ProjectionSource.BLANK, ProjectionKind.PRESET_DRAFT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source=default or source=model");
    }
}
