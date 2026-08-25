package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.projection.model.GoalieProjectionResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.service.mapping.PlayerIdOverrides;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectionSeedServiceTest {

    @Mock private ProjectionServiceClient projectionServiceClient;
    @Mock private PlayerPoolSource playerPool;

    private ProjectionSeedService service;

    @BeforeEach
    void setUp() {
        service = new ProjectionSeedService(
                projectionServiceClient,
                playerPool,
                new PlayerIdResolver(),
                new PlayerIdOverrides(""));
    }

    private static PlayerResponse nhlPlayer(int nhlId, String name, String team, Integer sweater) {
        PlayerResponse player = new PlayerResponse();
        player.setNhlId(nhlId);
        player.setFullName(name);
        player.setCurrentTeam(team);
        player.setSweaterNumber(sweater);
        player.setIsActive(true);
        return player;
    }

    private static SkaterProjectionResponse skater(int nhlId) {
        SkaterProjectionResponse p = new SkaterProjectionResponse();
        p.setNhlId(nhlId);
        p.setGamesPlayed(BigDecimal.valueOf(82));
        p.setToiPerGameSeconds(BigDecimal.valueOf(1320));
        p.setGoals(BigDecimal.valueOf(40));
        p.setAssists(BigDecimal.valueOf(60));
        p.setPoints(BigDecimal.valueOf(100));
        p.setShots(BigDecimal.valueOf(250));
        p.setShootingPct(BigDecimal.valueOf(0.16));
        return p;
    }

    private static GoalieProjectionResponse goalie(int nhlId, boolean withWorkload) {
        GoalieProjectionResponse p = new GoalieProjectionResponse();
        p.setNhlId(nhlId);
        p.setGamesPlayed(BigDecimal.valueOf(withWorkload ? 55 : 0));
        if (withWorkload) {
            p.setGamesStarted(BigDecimal.valueOf(54));
            p.setWins(BigDecimal.valueOf(30));
            p.setSavePct(BigDecimal.valueOf(0.912));
            p.setGoalsAgainstAvg(BigDecimal.valueOf(2.5));
        }
        return p;
    }

    private static SkaterResponse platformSkater(int id, String name, String team) {
        return platformSkater(id, name, team, null);
    }

    private static SkaterResponse platformSkater(int id, String name, String team, Integer sweater) {
        return new SkaterResponse(id, name, team, null, sweater, Set.of(), null);
    }

    private static GoalieResponse platformGoalie(int id, String name, String team) {
        return new GoalieResponse(id, name, team, null, null, null);
    }

    @Test
    @DisplayName("seeds under the platform's player id, not the NHL id")
    void seedsUnderPlatformId() {
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(nhlPlayer(8478402, "Connor McDavid", "EDM", 97)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(platformSkater(5000, "Connor McDavid", "EDM")));
        when(playerPool.getGoalies()).thenReturn(List.of());
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(8478402)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());

        ProjectionSeedService.Seed seed = service.seed(2026, "marcel-v3");

        assertThat(seed.players()).singleElement().satisfies(p -> {
            assertThat(p.getPlayerId()).isEqualTo(5000);
            assertThat(p.getType()).isEqualTo(PlayerProjection.TypeEnum.SKATER);
        });
        assertThat(seed.skatersSeeded()).isEqualTo(1);
    }

    @Test
    @DisplayName("translates the two services' stat vocabularies")
    void translatesStatKeys() {
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(nhlPlayer(1, "Connor McDavid", "EDM", 97)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(platformSkater(5000, "Connor McDavid", "EDM")));
        when(playerPool.getGoalies()).thenReturn(List.of());
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(1)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());

        var stats = service.seed(2026, "marcel-v3").players().get(0).getStats();

        assertThat(stats.getUtility()).containsEntry("gp", 82.0).containsEntry("toiPerGame", 1320.0);
        assertThat(stats.getScoring()).containsEntry("goals", 40.0).containsEntry("sog", 250.0);
        // The model holds a fraction; the app's column is a percentage.
        assertThat(stats.getScoring().get("shPct")).isEqualTo(16.0);
    }

    @Test
    @DisplayName("leaves out goalies the model projects no starts for")
    void skipsGoaliesWithoutWorkload() {
        // A goalie behind two starters has no save % to state. Seeding a .000 would read as the
        // worst goalie in the league rather than one who isn't expected to play.
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(
                        nhlPlayer(1, "Connor Hellebuyck", "WPG", 37),
                        nhlPlayer(2, "Third Stringer", "WPG", 50)));
        when(playerPool.getSkaters()).thenReturn(List.of());
        when(playerPool.getGoalies())
                .thenReturn(List.of(
                        platformGoalie(6000, "Connor Hellebuyck", "WPG"),
                        platformGoalie(6001, "Third Stringer", "WPG")));
        when(projectionServiceClient.skaterProjections(anyInt(), anyString())).thenReturn(List.of());
        when(projectionServiceClient.goalieProjections(anyInt(), anyString()))
                .thenReturn(List.of(goalie(1, true), goalie(2, false)));

        ProjectionSeedService.Seed seed = service.seed(2026, "marcel-v3");

        assertThat(seed.goaliesSeeded()).isEqualTo(1);
        assertThat(seed.withoutWorkload()).isEqualTo(1);
        assertThat(seed.players()).singleElement().satisfies(p -> {
            assertThat(p.getPlayerId()).isEqualTo(6000);
            assertThat(p.getStats().getScoring()).containsEntry("svPct", 0.912);
        });
    }

    @Test
    @DisplayName("counts players the platform does not carry instead of failing")
    void countsUnmapped() {
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(
                        nhlPlayer(1, "Connor McDavid", "EDM", 97),
                        nhlPlayer(2, "Jake Lucchini", "NSH", 15)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(platformSkater(5000, "Connor McDavid", "EDM")));
        when(playerPool.getGoalies()).thenReturn(List.of());
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(1), skater(2)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());

        ProjectionSeedService.Seed seed = service.seed(2026, "marcel-v3");

        assertThat(seed.skatersSeeded()).isEqualTo(1);
        assertThat(seed.unmapped()).isEqualTo(1);
    }

    @Test
    @DisplayName("a jersey number separates two players who share a name and a team")
    void sweaterSeparatesNamesakes() {
        // Vancouver carries two Elias Petterssons (the real ids and numbers). Their team cannot
        // tell them apart, so without the jersey number both fall out as ambiguous and neither
        // gets a projection at all.
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(
                        nhlPlayer(1, "Elias Pettersson", "VAN", 40),
                        nhlPlayer(2, "Elias Pettersson", "VAN", 25)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(
                        platformSkater(7520, "Elias Pettersson", "VAN", 40),
                        platformSkater(32762, "Elias Pettersson", "VAN", 25)));
        when(playerPool.getGoalies()).thenReturn(List.of());
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(1), skater(2)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());

        ProjectionSeedService.Seed seed = service.seed(2026, "marcel-v3");

        assertThat(seed.unmapped()).isZero();
        assertThat(seed.players())
                .extracting(PlayerProjection::getPlayerId)
                .containsExactlyInAnyOrder(7520, 32762);
    }

    @Test
    @DisplayName("omits a stat the model did not project rather than zeroing it")
    void omitsMissingStats() {
        // A zero is a claim; an absent value is not. Blocks aren't set on this projection.
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(nhlPlayer(1, "Connor McDavid", "EDM", 97)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(platformSkater(5000, "Connor McDavid", "EDM")));
        when(playerPool.getGoalies()).thenReturn(List.of());
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(1)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());

        var scoring = service.seed(2026, "marcel-v3").players().get(0).getStats().getScoring();

        assertThat(scoring).doesNotContainKey("blocks");
    }

    private static PlayerResponse retiredPlayer(
            int nhlId, String name, String team, Integer sweater) {
        PlayerResponse player = nhlPlayer(nhlId, name, team, sweater);
        player.setIsActive(false);
        return player;
    }

    @Test
    @DisplayName("a player who has left the league is seeded at zero, not left blank")
    void retiredPlayerIsZeroed() {
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of());
        when(projectionServiceClient.skaterProjections(anyInt(), anyString())).thenReturn(List.of());
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());
        when(projectionServiceClient.retiredPlayers())
                .thenReturn(List.of(retiredPlayer(8471685, "Anze Kopitar", "LA", 11)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(platformSkater(500, "Anze Kopitar", "LA", 11)));
        when(playerPool.getGoalies()).thenReturn(List.of());

        ProjectionSeedService.Seed seed = service.seed(2026, "marcel-v3");

        assertThat(seed.retiredZeroed()).isEqualTo(1);
        assertThat(seed.players()).hasSize(1);
        PlayerProjection zeroed = seed.players().get(0);
        assertThat(zeroed.getPlayerId()).isEqualTo(500);
        assertThat(zeroed.getStats().getScoring()).containsEntry("goals", 0.0);
        assertThat(zeroed.getStats().getScoring()).containsEntry("points", 0.0);
        assertThat(zeroed.getStats().getUtility()).containsEntry("gp", 0.0);
        // A complete row, so no stat is left for the UI to guess at.
        assertThat(zeroed.getStats().getScoring().values()).allMatch(v -> v == 0.0);
    }

    @Test
    @DisplayName("an active player keeps his projection when a retired one shares his name")
    void activeNamesakeIsNotZeroed() {
        // There really are two Sebastian Ahos. Zeroing the wrong one would wipe a first-round
        // forward off the board, so the active pass has to claim him before the retired pass runs.
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(nhlPlayer(8478427, "Sebastian Aho", "CAR", 20)));
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(8478427)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());
        when(projectionServiceClient.retiredPlayers())
                .thenReturn(List.of(retiredPlayer(8480222, "Sebastian Aho", "NYI", 28)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(platformSkater(600, "Sebastian Aho", "CAR", 20)));
        when(playerPool.getGoalies()).thenReturn(List.of());

        ProjectionSeedService.Seed seed = service.seed(2026, "marcel-v3");

        assertThat(seed.retiredZeroed()).isZero();
        assertThat(seed.players()).hasSize(1);
        assertThat(seed.players().get(0).getStats().getScoring())
                .containsEntry("points", 100.0);
    }

    @Test
    @DisplayName("a prospect with no NHL history is left alone, not zeroed")
    void prospectIsNotZeroed() {
        // A prospect is absent from the store entirely, so nothing on the retired side can
        // match him. Zeroing him would assert the model expects nothing, which it does not.
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of());
        when(projectionServiceClient.skaterProjections(anyInt(), anyString())).thenReturn(List.of());
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());
        when(projectionServiceClient.retiredPlayers())
                .thenReturn(List.of(retiredPlayer(8471685, "Anze Kopitar", "LA", 11)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(platformSkater(700, "Gavin McKenna", "PIT", 9)));
        when(playerPool.getGoalies()).thenReturn(List.of());

        ProjectionSeedService.Seed seed = service.seed(2026, "marcel-v3");

        assertThat(seed.retiredZeroed()).isZero();
        assertThat(seed.players()).isEmpty();
    }
}
