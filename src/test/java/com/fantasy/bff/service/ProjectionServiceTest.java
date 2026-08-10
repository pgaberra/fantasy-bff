package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.CreateProjectionRequest;
import com.fantasy.bff.dto.request.ProjectionSource;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.ProjectionData;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private DatabaseServiceClient databaseServiceClient;

    @Mock
    private PlayerService playerService;

    @Captor
    private ArgumentCaptor<com.fantasy.bff.generated.db.model.CreateProjectionRequest> sentRequest;

    private ProjectionService projectionService;

    @BeforeEach
    void setUp() {
        projectionService = new ProjectionService(databaseServiceClient, playerService, JsonMapper.builder().build());
    }

    @Test
    void withDefaultSource_fillsPlayersFromTheReadModelKeepingTheirStats() {
        givenOneSkaterAndOneGoalie();
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(new ProjectionResponse());

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
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(new ProjectionResponse());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.DEFAULT));

        assertThat(capturedPlayers().getFirst().getStats().getScoring())
                .containsKeys("goals", "assists", "points", "plusMinus", "pim", "ppg", "ppa", "shg", "sha",
                        "gwg", "sog", "shPct", "fw", "fl", "hits", "blocks");
    }

    @Test
    void withBlankSource_keepsEveryPlayerButZeroesTheStats() {
        givenOneSkaterAndOneGoalie();
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(new ProjectionResponse());

        projectionService.create(USER_ID, request(emptyData(), ProjectionSource.BLANK));

        List<PlayerProjection> players = capturedPlayers();
        assertThat(players).hasSize(2);
        assertThat(players.getFirst().getStats().getScoring().values()).containsOnly(0.0);
        assertThat(players.getFirst().getStats().getUtility().values()).containsOnly(0.0);
        assertThat(players.getLast().getStats().getScoring().values()).containsOnly(0.0);
    }

    @Test
    void withoutSource_passesTheClientsOwnPlayersThroughUntouched() {
        PlayerProjection own = new PlayerProjection()
                .playerId(7)
                .type(PlayerProjection.TypeEnum.SKATER)
                .stats(new PlayerStats().utility(Map.of("gp", 12.0)).scoring(Map.of("goals", 3.0)));
        when(databaseServiceClient.createProjection(eq(USER_ID), any())).thenReturn(new ProjectionResponse());

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
        verify(databaseServiceClient).createProjection(eq(USER_ID), sentRequest.capture());
        return sentRequest.getValue().getData().getPlayers();
    }

    private static CreateProjectionRequest request(ProjectionData data, ProjectionSource source) {
        return new CreateProjectionRequest("My Projection", data, source);
    }

    private static ProjectionData emptyData() {
        return new ProjectionData().settings(new ProjectionSettings());
    }

    private static ProjectionData dataWith(PlayerProjection player) {
        return new ProjectionData().settings(new ProjectionSettings()).players(List.of(player));
    }
}
