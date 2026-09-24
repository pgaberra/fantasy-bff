package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.config.AiProjectionProperties;
import com.fantasy.bff.config.SecurityProperties;
import com.fantasy.bff.dto.request.CreateProjectionRequest;
import com.fantasy.bff.client.DatabaseServiceClient.FollowedProjection;
import com.fantasy.bff.dto.request.CopyProjectionRequest;
import com.fantasy.bff.dto.request.ImportProjectionRequest;
import com.fantasy.bff.dto.request.ProjectionKind;
import com.fantasy.bff.dto.request.ProjectionSource;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.exception.PremiumRequiredException;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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

    @Mock
    private EntitlementService entitlementService;

    @Captor
    private ArgumentCaptor<com.fantasy.bff.generated.db.model.CreateProjectionRequest> sentRequest;

    private ProjectionService projectionService;

    @BeforeEach
    void setUp() {
        lenient().when(playerPool.playerIdSpace()).thenReturn(PlayerIdSpace.YAHOO);
        // Premium unless a test says otherwise: it is the state every other case here is about,
        // and the gate has its own tests below.
        lenient().when(entitlementService.hasPremiumAccess(USER_ID.toString())).thenReturn(true);
        projectionService = serviceWithAiProjection(true);
    }

    private ProjectionService serviceWithAiProjection(boolean enabled) {
        return serviceWith(enabled, true);
    }

    private ProjectionService serviceWith(boolean aiProjectionEnabled, boolean modelPrefixEnabled) {
        return new ProjectionService(
                databaseServiceClient,
                new PlayerPoolRows(playerService, JsonMapper.builder().build()),
                playerPool,
                reconciler,
                seedService,
                new AiProjectionAvailability(
                        new AiProjectionProperties(aiProjectionEnabled),
                        new SecurityProperties(null, null, null, modelPrefixEnabled)),
                entitlementService,
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
     * the two saves the result — otherwise every later read would redo the same work — and keeps
     * who was added until the owner acknowledges them, so the app can go on saying so.
     */
    @Test
    void aReadThatSquaredTheRowsWithThePoolSavesThemAndKeepsWhoWasAdded() {
        ProjectionResponse stored = storedProjection();
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(stored);
        when(reconciler.reconcile(stored.getData())).thenReturn(Optional.of(new Reconciliation(List.of(7, 8))));
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(stored);

        var response = projectionService.get(USER_ID, PROJECTION_ID);

        assertThat(response.data().settings().unacknowledgedNewPlayerIds()).containsExactly(7, 8);
        ArgumentCaptor<UpdateProjectionRequest> saved = ArgumentCaptor.forClass(UpdateProjectionRequest.class);
        verify(databaseServiceClient).updateProjection(eq(USER_ID), eq(PROJECTION_ID), saved.capture());
        assertThat(saved.getValue().getData().getPlayers()).isEqualTo(stored.getData().getPlayers());
        assertThat(saved.getValue().getData().getProjectionSettings().getUnacknowledgedNewPlayerIds())
                .containsExactly(7, 8);
    }

    /** A second pool change before the owner looked adds to the notice rather than replacing it. */
    @Test
    void playersAddedBeforeTheLastOnesWereAcknowledgedAreAddedToThem() {
        ProjectionResponse stored = storedProjection();
        stored.getData().getProjectionSettings().setUnacknowledgedNewPlayerIds(List.of(3, 7));
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(stored);
        when(reconciler.reconcile(stored.getData())).thenReturn(Optional.of(new Reconciliation(List.of(7, 8))));
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(stored);

        var response = projectionService.get(USER_ID, PROJECTION_ID);

        assertThat(response.data().settings().unacknowledgedNewPlayerIds()).containsExactly(3, 7, 8);
    }

    /** A pool change that added nobody leaves an unread notice exactly as it was. */
    @Test
    void aReadThatAddedNobodyLeavesTheUnacknowledgedPlayersAlone() {
        ProjectionResponse stored = storedProjection();
        stored.getData().getProjectionSettings().setUnacknowledgedNewPlayerIds(List.of(3));
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(stored);
        when(reconciler.reconcile(stored.getData())).thenReturn(Optional.of(new Reconciliation(List.of())));
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(stored);

        var response = projectionService.get(USER_ID, PROJECTION_ID);

        assertThat(response.data().settings().unacknowledgedNewPlayerIds()).containsExactly(3);
    }

    /** Saving the reconciliation is our business, not the caller's — losing it costs them nothing. */
    @Test
    void servesTheReconciledRowsEvenIfSavingThemFails() {
        ProjectionResponse stored = storedProjection();
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(stored);
        when(reconciler.reconcile(stored.getData())).thenReturn(Optional.of(new Reconciliation(List.of(7, 8))));
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenThrow(new IllegalStateException("db-service is down"));

        var response = projectionService.get(USER_ID, PROJECTION_ID);

        assertThat(response.data())
                .isEqualTo(com.fantasy.bff.dto.response.ProjectionData.from(stored.getData()));
        assertThat(response.data().settings().unacknowledgedNewPlayerIds()).containsExactly(7, 8);
    }

    /**
     * Between the id remap and this BFF serving the new pool, the stored rows and the pool are
     * numbered by different platforms. Squared anyway, every pool player would be added under
     * the other ids beside the stored rows, and that saved mixture cannot be taken apart again.
     */
    @Test
    void aProjectionKeyedByAnotherPlatformIsServedWithoutSquaringOrSaving() {
        ProjectionResponse stored = storedProjection().playerIdSpace(ProjectionResponse.PlayerIdSpaceEnum.ESPN);
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(stored);

        var response = projectionService.get(USER_ID, PROJECTION_ID);

        assertThat(response.data()).isEqualTo(com.fantasy.bff.dto.response.ProjectionData.from(stored.getData()));
        verifyNoInteractions(reconciler);
        verify(databaseServiceClient, never()).updateProjection(any(), any(), any());
    }

    @Test
    void aFollowKeyedByAnotherPlatformIsNotSquared() {
        ProjectionResponse followed = followedProjection()
                .playerIdSpace(ProjectionResponse.PlayerIdSpaceEnum.ESPN);
        when(databaseServiceClient.followShare(eq(USER_ID), any()))
                .thenReturn(new FollowedProjection(followed, true));

        projectionService.follow(USER_ID, new ImportProjectionRequest("token", null));

        verifyNoInteractions(reconciler);
        verify(databaseServiceClient, never()).updateProjection(any(), any(), any());
    }

    @Test
    void aCopyKeyedByAnotherPlatformIsNotSquared() {
        ProjectionResponse copied = storedProjection().playerIdSpace(ProjectionResponse.PlayerIdSpaceEnum.ESPN);
        when(databaseServiceClient.copyShare(eq(USER_ID), any())).thenReturn(copied);

        projectionService.copyFromShare(USER_ID, new CopyProjectionRequest("token", null));

        verifyNoInteractions(reconciler);
        verify(databaseServiceClient, never()).updateProjection(any(), any(), any());
    }

    /** The rows a client saves came from this BFF's pool, so they carry its numbering to db-service. */
    @Test
    void anUpdateIsSentWithThePoolsNumbering() {
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(storedProjection());

        projectionService.update(USER_ID, PROJECTION_ID, new UpdateProjectionRequest().name("My Projection"));

        ArgumentCaptor<UpdateProjectionRequest> sent = ArgumentCaptor.forClass(UpdateProjectionRequest.class);
        verify(databaseServiceClient).updateProjection(eq(USER_ID), eq(PROJECTION_ID), sent.capture());
        assertThat(sent.getValue().getPlayerIdSpace()).isEqualTo(UpdateProjectionRequest.PlayerIdSpaceEnum.YAHOO);
    }

    @Test
    void aReadWithNothingToSquareTouchesNothing() {
        ProjectionResponse stored = storedProjection();
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(stored);
        when(reconciler.reconcile(stored.getData())).thenReturn(Optional.empty());

        projectionService.get(USER_ID, PROJECTION_ID);

        verify(databaseServiceClient, never()).updateProjection(any(), any(), any());
    }

    /**
     * The model covers fewer players than the pool. Left to the first read, the ones it lacked
     * were added there and reported as having joined since the projection was created, seconds
     * earlier. Squared before it is written, the first read has nothing to report.
     */
    @Test
    void aNewProjectionIsSquaredWithThePoolBeforeItIsWritten() {
        PlayerProjection projected = new PlayerProjection().playerId(4242).type(PlayerProjection.TypeEnum.SKATER);
        when(seedService.seed(SEASON, MODEL_VERSION))
                .thenReturn(new ProjectionSeedService.Seed(List.of(projected), "marcel-v14", 1, 0, 0, 0, 0));
        PlayerProjection lacked = new PlayerProjection().playerId(9).type(PlayerProjection.TypeEnum.SKATER);
        when(reconciler.reconcile(any())).thenAnswer(invocation -> {
            ProjectionData data = invocation.getArgument(0);
            data.setPlayers(Stream.concat(data.getPlayers().stream(), Stream.of(lacked)).toList());
            return Optional.of(new Reconciliation(List.of(9)));
        });
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.MODEL));

        assertThat(capturedPlayers()).extracting(PlayerProjection::getPlayerId).containsExactly(4242, 9);
        assertThat(capturedSettings().getUnacknowledgedNewPlayerIds()).isNull();
    }

    /**
     * The new-projection page previews the AI starting point from these rows, so its top five
     * are the created board's top five only while both are the same rows. A stand-in reconciler
     * that squares only what it would square for real (a model basis, never squared) proves the
     * preview goes through the same filling and squaring as the create.
     */
    @Test
    void theModelBoard_isExactlyTheRowsAModelSeededProjectionIsWrittenWith() {
        PlayerProjection projected = new PlayerProjection().playerId(4242).type(PlayerProjection.TypeEnum.SKATER)
                .stats(new PlayerStats().utility(Map.of("gp", 81.5)).scoring(Map.of("goals", 49.4)));
        when(seedService.seed(SEASON, MODEL_VERSION))
                .thenReturn(new ProjectionSeedService.Seed(List.of(projected), "marcel-v14", 1, 0, 0, 0, 0));
        PlayerProjection lacked = new PlayerProjection().playerId(9).type(PlayerProjection.TypeEnum.GOALIE)
                .stats(new PlayerStats().utility(Map.of("gp", 60.0)).scoring(Map.of("w", 38.0)));
        givenAReconcilerThatAddsToAnUnsquaredModelBoard(lacked);
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        List<com.fantasy.bff.dto.response.PlayerProjection> board = projectionService.modelBoard();
        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.MODEL));

        assertThat(board).containsExactlyElementsOf(
                capturedPlayers().stream().map(com.fantasy.bff.dto.response.PlayerProjection::from).toList());
        assertThat(board).extracting(com.fantasy.bff.dto.response.PlayerProjection::playerId)
                .containsExactly(4242, 9);
    }

    /**
     * The rows a server-side starting point fills in have never been squared with any sync. A
     * stamp the client sent with its settings used to stand, and one matching the latest sync
     * had the reconciliation skip every player the model does not reach — a board the preview
     * of it would not match.
     */
    @Test
    void aSyncStampTheClientSent_doesNotSkipSquaringTheModelsRows() {
        PlayerProjection projected = new PlayerProjection().playerId(4242).type(PlayerProjection.TypeEnum.SKATER);
        when(seedService.seed(SEASON, MODEL_VERSION))
                .thenReturn(new ProjectionSeedService.Seed(List.of(projected), "marcel-v14", 1, 0, 0, 0, 0));
        PlayerProjection lacked = new PlayerProjection().playerId(9).type(PlayerProjection.TypeEnum.SKATER);
        givenAReconcilerThatAddsToAnUnsquaredModelBoard(lacked);
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());
        ProjectionData claimed = emptyData();
        claimed.getProjectionSettings().setPlayerPoolSyncedAt(OffsetDateTime.parse("2026-09-24T04:00:00Z"));

        projectionService.create(USER_ID, request(claimed, ProjectionSource.MODEL));

        assertThat(capturedPlayers()).extracting(PlayerProjection::getPlayerId).containsExactly(4242, 9);
    }

    private void givenAReconcilerThatAddsToAnUnsquaredModelBoard(PlayerProjection lacked) {
        when(reconciler.reconcile(any())).thenAnswer(invocation -> {
            ProjectionData data = invocation.getArgument(0);
            ProjectionSettings settings = data.getProjectionSettings();
            if (settings.getPlayerBasis() != PlayerBasisEnum.MODEL || settings.getPlayerPoolSyncedAt() != null) {
                return Optional.empty();
            }
            data.setPlayers(Stream.concat(data.getPlayers().stream(), Stream.of(lacked)).toList());
            settings.setPlayerPoolSyncedAt(OffsetDateTime.parse("2026-09-24T04:00:00Z"));
            return Optional.of(new Reconciliation(List.of(lacked.getPlayerId())));
        });
    }

    /** A copied board brings its source's settings, and the source's unread notice is not the copy's. */
    @Test
    void aCopyDoesNotInheritItsSourcesUnacknowledgedPlayers() {
        PlayerProjection own = new PlayerProjection().playerId(7).type(PlayerProjection.TypeEnum.SKATER);
        ProjectionData copied = dataWith(own);
        copied.getProjectionSettings().setUnacknowledgedNewPlayerIds(List.of(7));
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(copied, null));

        assertThat(capturedSettings().getUnacknowledgedNewPlayerIds()).isNull();
    }

    /** A shared board is the author's pool, not ours, so a copy is squared and saved as it arrives. */
    @Test
    void aCopyIsSquaredWithThePoolAndSavedWithoutReportingIt() {
        ProjectionResponse copied = storedProjection();
        when(databaseServiceClient.copyShare(eq(USER_ID), any())).thenReturn(copied);
        when(reconciler.reconcile(copied.getData())).thenReturn(Optional.of(new Reconciliation(List.of(7, 8))));
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any())).thenReturn(copied);

        projectionService.copyFromShare(USER_ID, new CopyProjectionRequest("token", null));

        ArgumentCaptor<UpdateProjectionRequest> saved = ArgumentCaptor.forClass(UpdateProjectionRequest.class);
        verify(databaseServiceClient).updateProjection(eq(USER_ID), eq(PROJECTION_ID), saved.capture());
        assertThat(saved.getValue().getData().getProjectionSettings().getUnacknowledgedNewPlayerIds()).isNullOrEmpty();
    }

    @Test
    void aCopyAlreadySquaredWithThePoolIsNotWrittenAgain() {
        ProjectionResponse copied = storedProjection();
        when(databaseServiceClient.copyShare(eq(USER_ID), any())).thenReturn(copied);
        when(reconciler.reconcile(copied.getData())).thenReturn(Optional.empty());

        projectionService.copyFromShare(USER_ID, new CopyProjectionRequest("token", null));

        verify(databaseServiceClient, never()).updateProjection(any(), any(), any());
    }

    /**
     * A follow holds nothing of the follower's but their draft, so db-service would store none
     * of the reconciled rows: the write is skipped and the rows are squared in memory instead.
     */
    @Test
    void aFollowIsSquaredInMemoryAndNeverWritten() {
        ProjectionResponse followed = followedProjection();
        when(databaseServiceClient.followShare(eq(USER_ID), any()))
                .thenReturn(new FollowedProjection(followed, true));
        when(reconciler.reconcile(followed.getData()))
                .thenReturn(Optional.of(new Reconciliation(List.of(7, 8))));

        var response = projectionService.follow(USER_ID, new ImportProjectionRequest("token", null));

        assertThat(response.created()).isTrue();
        verify(reconciler).reconcile(followed.getData());
        verify(databaseServiceClient, never()).updateProjection(any(), any(), any());
        assertThat(response.projection().data().settings().unacknowledgedNewPlayerIds()).isNullOrEmpty();
    }

    /**
     * Every read of a follow squares it again, since nothing was stored to settle it — and the
     * players that turns up are never reported: the follower did not build this board and
     * cannot edit it, and the author's own unread notice travelled across with the publish.
     */
    @Test
    void readingAFollowSquaresItInMemoryWithoutWritingOrReportingNewPlayers() {
        ProjectionResponse followed = followedProjection();
        followed.getData().getProjectionSettings().setUnacknowledgedNewPlayerIds(List.of(3));
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(followed);
        when(reconciler.reconcile(followed.getData()))
                .thenReturn(Optional.of(new Reconciliation(List.of(7, 8))));

        var response = projectionService.get(USER_ID, PROJECTION_ID);

        verify(reconciler).reconcile(followed.getData());
        verify(databaseServiceClient, never()).updateProjection(any(), any(), any());
        assertThat(response.data().settings().unacknowledgedNewPlayerIds()).isNullOrEmpty();
        assertThat(response.origin().shareToken()).isEqualTo("t0k3n");
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

    @Test
    @DisplayName("an imported board sent with its own rows is stored as imported, with those rows")
    void importedWithOwnRows_isStoredAsImported() {
        PlayerProjection own = new PlayerProjection()
                .playerId(7)
                .type(PlayerProjection.TypeEnum.SKATER)
                .stats(new PlayerStats().utility(Map.of("gp", 12.0)).scoring(Map.of("goals", 3.0)));
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(dataWith(own), null, ProjectionKind.IMPORTED));

        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        assertThat(sentRequest.getValue().getKind())
                .isEqualTo(com.fantasy.bff.generated.db.model.CreateProjectionRequest.KindEnum.IMPORTED);
        assertThat(sentRequest.getValue().getName()).isEqualTo("My Projection");
        assertThat(sentRequest.getValue().getPreset()).isNull();
        assertThat(sentRequest.getValue().getData().getPlayers()).containsExactly(own);
        verifyNoInteractions(playerService);
    }

    @Test
    @DisplayName("an imported board cannot ask the server to fill its rows")
    void importedWithSource_isRejected() {
        CreateProjectionRequest request = request(emptyData(), ProjectionSource.DEFAULT, ProjectionKind.IMPORTED);

        assertThatThrownBy(() -> projectionService.create(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imported board");

        verifyNoInteractions(databaseServiceClient);
    }

    @Test
    @DisplayName("an imported board naming the model is refused for its shape, before any premium lookup")
    void importedWithModelSource_isRejectedBeforeThePremiumCheck() {
        CreateProjectionRequest request = request(emptyData(), ProjectionSource.MODEL, ProjectionKind.IMPORTED);

        assertThatThrownBy(() -> projectionService.create(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imported board");

        verifyNoInteractions(entitlementService, seedService, databaseServiceClient);
    }

    @Test
    @DisplayName("a projection, named or defaulted, is still stored as the user's own")
    void projectionKind_isStoredAsProjection() {
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID,
                request(dataWith(new PlayerProjection().playerId(7)), null, ProjectionKind.PROJECTION));
        projectionService.create(USER_ID, request(dataWith(new PlayerProjection().playerId(8)), null));

        verify(databaseServiceClient, times(2)).createProjection(eq(USER_ID), sentRequest.capture());
        assertThat(sentRequest.getAllValues())
                .extracting(com.fantasy.bff.generated.db.model.CreateProjectionRequest::getKind)
                .containsOnly(com.fantasy.bff.generated.db.model.CreateProjectionRequest.KindEnum.PROJECTION);
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

    /** A board the user follows: db-service's mirror of someone else's, marked by its origin. */
    private static ProjectionResponse followedProjection() {
        return storedProjection()
                .kind(ProjectionResponse.KindEnum.IMPORTED)
                .origin(new com.fantasy.bff.generated.db.model.ProjectionOrigin()
                        .shareToken("t0k3n")
                        .authorUsername("alex"));
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
                .thenReturn(new ProjectionSeedService.Seed(List.of(projected), "marcel-v14", 1, 0, 0, 0, 0));
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
    @DisplayName("model source is refused when the AI projection is switched off")
    void withModelSource_whenAiProjectionIsOff_isRefused() {
        ProjectionService service = serviceWithAiProjection(false);

        assertThatThrownBy(() -> service.create(USER_ID, request(emptyData(), ProjectionSource.MODEL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("switched off");

        // The refusal comes before anything else the request would do, so the model is never
        // asked and nothing reaches the database.
        verifyNoInteractions(seedService);
        verifyNoInteractions(databaseServiceClient);
    }

    /**
     * The seed endpoint is closed with the model prefix, and a model-seeded projection is the
     * same lines by another door. Leaving it open would serve the model in an environment that
     * decided not to, and have the web offer a preset whose preview is refused.
     */
    @Test
    @DisplayName("model source is refused when the model endpoints are closed, even with the AI projection on")
    void withModelSource_whenTheModelPrefixIsClosed_isRefused() {
        ProjectionService service = serviceWith(true, false);

        assertThatThrownBy(() -> service.create(USER_ID, request(emptyData(), ProjectionSource.MODEL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("switched off");

        verifyNoInteractions(seedService);
        verifyNoInteractions(databaseServiceClient);
        verifyNoInteractions(entitlementService);
    }

    @Test
    @DisplayName("switching the AI projection off leaves the other starting points alone")
    void whenAiProjectionIsOff_theOtherSourcesStillFillTheirRows() {
        ProjectionService service = serviceWithAiProjection(false);
        givenOneSkaterAndOneGoalie();
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        service.create(USER_ID, request(emptyData(), ProjectionSource.DEFAULT));

        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        assertThat(sentRequest.getValue().getData().getPlayers()).hasSize(2);
    }

    @Test
    @DisplayName("a model-seeded projection records the model as its basis")
    void withModelSource_recordsTheModelBasis() {
        // playerBasis answers "what should a player who joins the pool later be seeded with".
        // Left unset, the reconciliation before the write read the model's rows as last season's.
        when(seedService.seed(SEASON, MODEL_VERSION))
                .thenReturn(new ProjectionSeedService.Seed(List.of(), "marcel-v14", 0, 0, 0, 0, 0));
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.MODEL));

        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        assertThat(sentRequest.getValue().getData().getProjectionSettings().getPlayerBasis())
                .isEqualTo(PlayerBasisEnum.MODEL);
    }

    /**
     * A model basis makes the reconciler fill in the model's lines, so a client without premium
     * cannot claim one for rows it sent itself — the next reconciliation would otherwise fill
     * every row it left out from the model.
     */
    @Test
    void aCopyClaimingTheModelBasisWithoutPremium_isRecordedAsLastSeason() {
        when(entitlementService.hasPremiumAccess(USER_ID.toString())).thenReturn(false);
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(dataWithBasis(PlayerBasisEnum.MODEL), null));

        assertThat(capturedSettings().getPlayerBasis()).isEqualTo(PlayerBasisEnum.LAST_SEASON);
    }

    @Test
    void aCopyOfAModelProjectionWithPremium_keepsTheModelBasis() {
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(dataWithBasis(PlayerBasisEnum.MODEL), null));

        assertThat(capturedSettings().getPlayerBasis()).isEqualTo(PlayerBasisEnum.MODEL);
    }

    /** A lapsed subscription keeps the AI projection it started, and its app sends the basis back on save. */
    @Test
    void savingAModelProjectionAfterPremiumLapsed_keepsTheModelBasis() {
        when(entitlementService.hasPremiumAccess(USER_ID.toString())).thenReturn(false);
        ProjectionResponse stored = storedProjection();
        stored.getData().getProjectionSettings().setPlayerBasis(PlayerBasisEnum.MODEL);
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(stored);
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any())).thenReturn(stored);

        projectionService.update(USER_ID, PROJECTION_ID, updateWithBasis(PlayerBasisEnum.MODEL));

        assertThat(sentUpdateBasis()).isEqualTo(PlayerBasisEnum.MODEL);
    }

    @Test
    void savingAModelBasisOntoAProjectionThatNeverHadIt_withoutPremium_isRecordedAsLastSeason() {
        when(entitlementService.hasPremiumAccess(USER_ID.toString())).thenReturn(false);
        when(databaseServiceClient.getProjection(USER_ID, PROJECTION_ID)).thenReturn(storedProjection());
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(storedProjection());

        projectionService.update(USER_ID, PROJECTION_ID, updateWithBasis(PlayerBasisEnum.MODEL));

        assertThat(sentUpdateBasis()).isEqualTo(PlayerBasisEnum.LAST_SEASON);
    }

    /** The stored projection is read only when it could change the answer. */
    @Test
    void savingAnyOtherBasis_readsNothingFirst() {
        when(databaseServiceClient.updateProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(storedProjection());

        projectionService.update(USER_ID, PROJECTION_ID, updateWithBasis(PlayerBasisEnum.BLANK));

        assertThat(sentUpdateBasis()).isEqualTo(PlayerBasisEnum.BLANK);
        verify(databaseServiceClient, never()).getProjection(any(), any());
    }

    private static ProjectionData dataWithBasis(PlayerBasisEnum basis) {
        ProjectionData data = dataWith(new PlayerProjection()
                .playerId(7)
                .type(PlayerProjection.TypeEnum.SKATER)
                .stats(new PlayerStats().utility(Map.of("gp", 12.0)).scoring(Map.of("goals", 3.0))));
        data.getProjectionSettings().setPlayerBasis(basis);
        return data;
    }

    private static UpdateProjectionRequest updateWithBasis(PlayerBasisEnum basis) {
        return new UpdateProjectionRequest()
                .name("My Projection")
                .data(new com.fantasy.bff.generated.db.model.UpdateProjectionData()
                        .projectionSettings(new ProjectionSettings().playerBasis(basis)));
    }

    private PlayerBasisEnum sentUpdateBasis() {
        ArgumentCaptor<UpdateProjectionRequest> sent = ArgumentCaptor.forClass(UpdateProjectionRequest.class);
        verify(databaseServiceClient).updateProjection(eq(USER_ID), eq(PROJECTION_ID), sent.capture());
        return sent.getValue().getData().getProjectionSettings().getPlayerBasis();
    }

    @Test
    @DisplayName("a preset draft may be started from the model, and records that preset")
    void presetDraftFromModel_recordsThePreset() {
        when(seedService.seed(SEASON, MODEL_VERSION))
                .thenReturn(new ProjectionSeedService.Seed(List.of(), "marcel-v14", 0, 0, 0, 0, 0));
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(
                USER_ID,
                request(emptyData(), ProjectionSource.MODEL, ProjectionKind.DRAFT));

        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        // The name is the caller's, here as anywhere: a draft can be renamed the moment it
        // exists, so overruling the caller only ever held for the seconds in between.
        assertThat(sentRequest.getValue().getName()).isEqualTo("My Projection");
        // What it was drafted against is stored outright instead, so nothing downstream has to
        // read it back out of a name its owner may since have changed.
        assertThat(sentRequest.getValue().getPreset())
                .isEqualTo(com.fantasy.bff.generated.db.model.CreateProjectionRequest.PresetEnum.MODEL);
    }

    @Test
    @DisplayName("a preset draft from last season's stats records that preset")
    void presetDraftFromDefault_recordsThePreset() {
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(
                USER_ID,
                request(emptyData(), ProjectionSource.DEFAULT, ProjectionKind.DRAFT));

        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        assertThat(sentRequest.getValue().getPreset())
                .isEqualTo(com.fantasy.bff.generated.db.model.CreateProjectionRequest.PresetEnum.LAST_SEASON);
    }

    @Test
    @DisplayName("a projection the user made carries no preset")
    void ownProjection_hasNoPreset() {
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.DEFAULT));

        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        assertThat(sentRequest.getValue().getPreset()).isNull();
    }

    @Test
    @DisplayName("a preset draft still cannot be started from a blank pool")
    void presetDraftFromBlank_isRejected() {
        assertThatThrownBy(() -> projectionService.create(
                        USER_ID,
                        request(emptyData(), ProjectionSource.BLANK, ProjectionKind.DRAFT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source=default or source=model");
    }

    /**
     * The model's lines are what premium pays for, and a preset draft seeded from them is a way
     * to the same numbers that never touches /projection-model/seed. Both doors are locked.
     */
    @Test
    @DisplayName("a model-seeded projection is refused to an account without premium")
    void modelSource_withoutPremium_isRefused() {
        when(entitlementService.hasPremiumAccess(USER_ID.toString())).thenReturn(false);

        assertThatThrownBy(() -> projectionService.create(USER_ID, request(emptyData(), ProjectionSource.MODEL)))
                .isInstanceOf(PremiumRequiredException.class)
                .hasMessageContaining("premium");

        verifyNoInteractions(seedService);
        verifyNoInteractions(databaseServiceClient);
    }

    @Test
    @DisplayName("and so is a preset draft started from the model, which is the same numbers")
    void modelPresetDraft_withoutPremium_isRefused() {
        when(entitlementService.hasPremiumAccess(USER_ID.toString())).thenReturn(false);

        CreateProjectionRequest request = new CreateProjectionRequest(
                "AI Projection", ProjectionKind.DRAFT, emptyData(), ProjectionSource.MODEL);

        assertThatThrownBy(() -> projectionService.create(USER_ID, request))
                .isInstanceOf(PremiumRequiredException.class);

        verifyNoInteractions(seedService);
    }

    /**
     * Only the model is sold. Last season's stats are the free starting point and must not be
     * caught by the same gate, so the subscription is never even looked up for them.
     */
    @Test
    @DisplayName("the free starting point is untouched, and costs no subscription lookup")
    void defaultSource_withoutPremium_isServed() {
        givenOneSkaterAndOneGoalie();
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(created());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.DEFAULT));

        verify(databaseServiceClient).createProjection(eq(USER_ID), any());
        verifyNoInteractions(entitlementService);
    }

    /**
     * An environment that does not sell the AI projection at all answers about the switch, not
     * about the plan: "switched off here" is the truth, and "subscribe" would point at a page
     * that cannot make it appear.
     */
    @Test
    @DisplayName("with the feature switched off the answer is the switch, not the subscription")
    void modelSource_withFeatureOff_reportsTheSwitch() {
        projectionService = serviceWithAiProjection(false);

        assertThatThrownBy(() -> projectionService.create(USER_ID, request(emptyData(), ProjectionSource.MODEL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("switched off");
        verifyNoInteractions(entitlementService);
    }
}
