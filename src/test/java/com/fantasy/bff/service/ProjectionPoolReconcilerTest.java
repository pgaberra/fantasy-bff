package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.ProjectionData;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum;
import com.fantasy.bff.service.ProjectionPoolReconciler.Reconciliation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * The pool a projection was written against stops being the pool a season later, and a saved
 * projection has to survive that: it gains the players who arrived, seeded from whatever it
 * started as, and keeps the rows of the ones who left rather than deleting work that a bad
 * fetch could have invented a reason for.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectionPoolReconcilerTest {

    private static final OffsetDateTime LAST_SYNC = OffsetDateTime.parse("2026-08-16T04:12:00Z");

    @Mock
    private PlayerPoolSource playerPool;

    @Mock
    private PlayerService playerService;

    private ProjectionPoolReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new ProjectionPoolReconciler(
                playerPool, new PlayerPoolRows(playerService, JsonMapper.builder().build()));
        givenLastSuccessfulSyncAt(LAST_SYNC);
        when(playerService.getSkaters()).thenReturn(List.of(skater(1), skater(2)));
        when(playerService.getGoalies()).thenReturn(List.of(goalie(101)));
    }

    @Test
    void addsThePlayersThePoolHasGained() {
        ProjectionData data = projection(PlayerBasisEnum.LAST_SEASON, null, row(1));

        Reconciliation change = reconciler.reconcile(data).orElseThrow();

        assertThat(change).isEqualTo(new Reconciliation(2));
        assertThat(playerIds(data)).containsExactly(1, 2, 101);
    }

    /**
     * The promise this rests on. Player 99 is not in the pool, and the row carrying the user's
     * numbers for them stays — hidden by the client, not deleted here. A truncated fetch or a
     * player Yahoo briefly stops listing would otherwise cost work that cannot be recovered.
     */
    @Test
    void keepsARowWhosePlayerHasLeftThePool() {
        PlayerProjection departed = row(99);
        ProjectionData data = projection(PlayerBasisEnum.LAST_SEASON, null, row(1), departed);

        Reconciliation change = reconciler.reconcile(data).orElseThrow();

        assertThat(change.added()).isEqualTo(2);
        assertThat(playerIds(data)).contains(99);
        assertThat(data.getPlayers()).contains(departed);
    }

    /** A pool that lost every player it had still costs the projection nothing. */
    @Test
    void keepsEveryRowEvenWhenNoneOfThemAreInThePoolAnyMore() {
        ProjectionData data = projection(PlayerBasisEnum.LAST_SEASON, null, row(97), row(98), row(99));

        reconciler.reconcile(data);

        assertThat(playerIds(data)).contains(97, 98, 99);
    }

    /** A projection built on last season's stats should gain that player's line, not a blank row. */
    @Test
    void seedsAGainedPlayerFromLastSeasonWhenThatIsWhatTheProjectionIsBuiltOn() {
        ProjectionData data = projection(PlayerBasisEnum.LAST_SEASON, null, row(1));

        reconciler.reconcile(data);

        assertThat(gained(data, 2).getStats().getScoring()).containsEntry("goals", 64.0);
    }

    @Test
    void seedsAGainedPlayerAtZeroWhenTheProjectionWasStartedFromScratch() {
        ProjectionData data = projection(PlayerBasisEnum.BLANK, null, row(1));

        reconciler.reconcile(data);

        assertThat(gained(data, 2).getStats().getScoring().values()).containsOnly(0.0);
        assertThat(gained(data, 2).getStats().getUtility().values()).containsOnly(0.0);
    }

    /**
     * Projections written before the basis was recorded still have to be seeded correctly, and
     * the two starting points are far enough apart to read off the rows themselves.
     */
    @Test
    void readsAnUnrecordedBasisOffTheRows() {
        ProjectionData allZeros = projection(null, null, blankRow(1));

        reconciler.reconcile(allZeros);

        assertThat(allZeros.getSettings().getPlayerBasis()).isEqualTo(PlayerBasisEnum.BLANK);
        assertThat(gained(allZeros, 2).getStats().getScoring().values()).containsOnly(0.0);
    }

    @Test
    void readsRowsWithRealStatsAsBuiltOnLastSeason() {
        ProjectionData scored = projection(null, null, row(1));

        reconciler.reconcile(scored);

        assertThat(scored.getSettings().getPlayerBasis()).isEqualTo(PlayerBasisEnum.LAST_SEASON);
    }

    @Test
    void stampsTheSyncItSquaredTheRowsWith() {
        ProjectionData data = projection(PlayerBasisEnum.LAST_SEASON, null, row(1));

        reconciler.reconcile(data);

        assertThat(data.getSettings().getPlayerPoolSyncedAt()).isEqualTo(LAST_SYNC);
    }

    /** The guard: rows already squared with the latest sync must not cost a pool read at all. */
    @Test
    void doesNothingWhenTheRowsAreAlreadySquaredWithTheLatestSync() {
        ProjectionData data = projection(PlayerBasisEnum.LAST_SEASON, LAST_SYNC, row(1));

        assertThat(reconciler.reconcile(data)).isEmpty();
        assertThat(playerIds(data)).containsExactly(1);
    }

    @Test
    void reconcilesAgainWhenAFreshSyncHasRunSince() {
        ProjectionData data = projection(
                PlayerBasisEnum.LAST_SEASON, LAST_SYNC.minusDays(1), row(1));

        assertThat(reconciler.reconcile(data)).isPresent();
        assertThat(playerIds(data)).containsExactly(1, 2, 101);
    }

    /** Running twice over the same pool must not add a player a second time. */
    @Test
    void addsNothingTwice() {
        ProjectionData data = projection(PlayerBasisEnum.LAST_SEASON, null, row(1));

        reconciler.reconcile(data);
        data.getSettings().setPlayerPoolSyncedAt(null);
        Reconciliation again = reconciler.reconcile(data).orElseThrow();

        assertThat(again.added()).isZero();
        assertThat(playerIds(data)).containsExactly(1, 2, 101);
    }

    /**
     * The pool is the only thing that says which players exist. A read that fails, or one that
     * comes back empty, must leave the projection alone rather than drop every row it cannot
     * account for.
     */
    @Test
    void addsNothingWhenThePoolCannotBeRead() {
        when(playerService.getSkaters()).thenThrow(new IllegalStateException("yahoo-service is down"));
        ProjectionData data = projection(PlayerBasisEnum.LAST_SEASON, null, row(1), row(99));

        assertThat(reconciler.reconcile(data)).isEmpty();
        assertThat(playerIds(data)).containsExactly(1, 99);
    }

    @Test
    void addsNothingWhenThePoolComesBackEmpty() {
        when(playerService.getSkaters()).thenReturn(List.of());
        when(playerService.getGoalies()).thenReturn(List.of());
        ProjectionData data = projection(PlayerBasisEnum.LAST_SEASON, null, row(1), row(99));

        assertThat(reconciler.reconcile(data)).isEmpty();
        assertThat(playerIds(data)).containsExactly(1, 99);
    }

    /** Nothing has ever been synced, so there is no pool state to square anything with. */
    @Test
    void doesNothingBeforeTheFirstSuccessfulSync() {
        when(playerPool.lastSyncedAt()).thenReturn(Optional.empty());
        ProjectionData data = projection(PlayerBasisEnum.LAST_SEASON, null, row(1));

        assertThat(reconciler.reconcile(data)).isEmpty();
    }

    private void givenLastSuccessfulSyncAt(OffsetDateTime finishedAt) {
        when(playerPool.lastSyncedAt()).thenReturn(Optional.of(finishedAt));
    }

    private static SkaterResponse skater(int id) {
        return new SkaterResponse(
                id, "Skater " + id, "EDM", "https://example.test/" + id + ".png", 97,
                Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(82, 1320),
                        new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1, 23, 38, 61,
                                8, 2, 348, 18.4, 812, 623, 42, 28, 0, 1408, 92400)));
    }

    private static GoalieResponse goalie(int id) {
        return new GoalieResponse(
                id, "Goalie " + id, "NYR", "https://example.test/" + id + ".png", 31,
                new GoalieResponse.Stats(
                        new GoalieResponse.UtilityStats(58),
                        new GoalieResponse.ScoringStats(58, 36, 17, 4, 3, 1720, 1565, 155, 2.67, 0.910,
                                0.632, 209000)));
    }

    private static ProjectionData projection(PlayerBasisEnum basis, OffsetDateTime syncedAt,
                                             PlayerProjection... players) {
        return new ProjectionData()
                .settings(new ProjectionSettings().playerBasis(basis).playerPoolSyncedAt(syncedAt))
                .players(new ArrayList<>(List.of(players)));
    }

    private static PlayerProjection row(int playerId) {
        return new PlayerProjection()
                .playerId(playerId)
                .type(PlayerProjection.TypeEnum.SKATER)
                .stats(new PlayerStats().utility(Map.of("gp", 70.0)).scoring(Map.of("goals", 30.0)));
    }

    private static PlayerProjection blankRow(int playerId) {
        return new PlayerProjection()
                .playerId(playerId)
                .type(PlayerProjection.TypeEnum.SKATER)
                .stats(new PlayerStats().utility(Map.of("gp", 0.0)).scoring(Map.of("goals", 0.0)));
    }

    private static List<Integer> playerIds(ProjectionData data) {
        return data.getPlayers().stream().map(PlayerProjection::getPlayerId).toList();
    }

    private static PlayerProjection gained(ProjectionData data, int playerId) {
        return data.getPlayers().stream()
                .filter(player -> player.getPlayerId() == playerId)
                .findFirst()
                .orElseThrow();
    }
}
