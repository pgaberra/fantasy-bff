package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
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
                new PlayerIdOverrides(""),
                new ProjectionSeedCache());
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
        p.setHatTricks(BigDecimal.valueOf(1.1));
        p.setShifts(BigDecimal.valueOf(1700));
        p.setPpGoals(BigDecimal.valueOf(12));
        p.setPpAssists(BigDecimal.valueOf(20));
        p.setPpPoints(BigDecimal.valueOf(32));
        p.setShGoals(BigDecimal.valueOf(2));
        p.setShAssists(BigDecimal.valueOf(1));
        p.setShPoints(BigDecimal.valueOf(3));
        return p;
    }

    private static GoalieProjectionResponse goalie(int nhlId, boolean withWorkload) {
        GoalieProjectionResponse p = new GoalieProjectionResponse();
        p.setNhlId(nhlId);
        if (withWorkload) {
            p.setGamesPlayed(BigDecimal.valueOf(55));
            p.setGamesStarted(BigDecimal.valueOf(54));
            p.setWins(BigDecimal.valueOf(30));
            p.setLosses(BigDecimal.valueOf(18));
            p.setOtLosses(BigDecimal.valueOf(6));
            p.setSavePct(BigDecimal.valueOf(0.912));
            p.setGoalsAgainstAvg(BigDecimal.valueOf(2.5));
            p.setToiSeconds(BigDecimal.valueOf(55 * 58 * 60));
            return p;
        }
        // What projection-service sends for a goalie its crease has no room for: every volume at
        // nought, and no save % or GAA, since there are no shots for either to be a rate of.
        p.setGamesPlayed(BigDecimal.ZERO);
        p.setGamesStarted(BigDecimal.ZERO);
        p.setWins(BigDecimal.ZERO);
        p.setLosses(BigDecimal.ZERO);
        p.setOtLosses(BigDecimal.ZERO);
        p.setShutouts(BigDecimal.ZERO);
        p.setShotsAgainst(BigDecimal.ZERO);
        p.setSaves(BigDecimal.ZERO);
        p.setGoalsAgainst(BigDecimal.ZERO);
        p.setToiSeconds(BigDecimal.ZERO);
        return p;
    }

    private static SkaterProjectionResponse skater(int nhlId, double points) {
        SkaterProjectionResponse p = skater(nhlId);
        p.setPoints(BigDecimal.valueOf(points));
        return p;
    }

    private static GoalieProjectionResponse goalie(int nhlId, double wins) {
        GoalieProjectionResponse p = goalie(nhlId, true);
        p.setWins(BigDecimal.valueOf(wins));
        return p;
    }

    private static SkaterResponse platformSkater(int id, String name, String team) {
        return platformSkater(id, name, team, null);
    }

    private static SkaterResponse platformSkater(int id, String name, String team, Integer sweater) {
        return new SkaterResponse(id, name, team, null, sweater, Set.of(), null);
    }

    private static SkaterResponse platformDefenceman(int id, String name, String team) {
        return new SkaterResponse(id, name, team, null, null, Set.of(SkaterPosition.D), null);
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
        // A fraction of a hat trick is what the model has to say about one: they are rare
        // enough that a whole number would be a claim it cannot make.
        assertThat(stats.getScoring()).containsEntry("hatTricks", 1.1);
        assertThat(stats.getScoring()).containsEntry("shifts", 1700.0);
    }

    @Test
    @DisplayName("adds up the special-teams categories the model has no column for")
    void sumsSpecialTeamsCategories() {
        seedOneSkater(platformSkater(5000, "Connor McDavid", "EDM"));

        var scoring = service.seed(2026, "marcel-v3").players().get(0).getStats().getScoring();

        // Power play plus shorthanded, which is one category in the leagues that score it.
        assertThat(scoring).containsEntry("stpg", 14.0).containsEntry("stpa", 21.0);
        assertThat(scoring).containsEntry("stp", 35.0);
        // The model reports ice time per game; the column is the season's total.
        assertThat(scoring).containsEntry("toi", 82.0 * 1320);
    }

    @Test
    @DisplayName("scores a defenceman's points as defencemen points")
    void defencePointsForADefenceman() {
        seedOneSkater(platformDefenceman(5000, "Cale Makar", "COL"));

        assertThat(service.seed(2026, "marcel-v3").players().get(0).getStats().getScoring())
                .containsEntry("defPoints", 100.0);
    }

    @Test
    @DisplayName("leaves defencemen points off a forward rather than seeding a nought")
    void noDefencePointsForAForward() {
        // A forward has none of this category, and a nought would be scored as if he had
        // produced none of something he cannot produce at all.
        seedOneSkater(platformSkater(5000, "Connor McDavid", "EDM"));

        assertThat(service.seed(2026, "marcel-v3").players().get(0).getStats().getScoring())
                .doesNotContainKey("defPoints");
    }

    @Test
    @DisplayName("seeds a goalie's overtime losses, minutes and win percentage")
    void seedsGoalieDecisionColumns() {
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(nhlPlayer(1, "Igor Shesterkin", "NYR", 31)));
        when(playerPool.getSkaters()).thenReturn(List.of());
        when(playerPool.getGoalies())
                .thenReturn(List.of(platformGoalie(6000, "Igor Shesterkin", "NYR")));
        when(projectionServiceClient.skaterProjections(anyInt(), anyString())).thenReturn(List.of());
        when(projectionServiceClient.goalieProjections(anyInt(), anyString()))
                .thenReturn(List.of(goalie(1, true)));

        var scoring = service.seed(2026, "marcel-v3").players().get(0).getStats().getScoring();

        assertThat(scoring).containsEntry("otl", 6.0);
        assertThat(scoring).containsEntry("toi", (double) (55 * 58 * 60));
        // An overtime loss is a decision like any other: 30 of 54.
        assertThat(scoring).containsEntry("winPct", 0.556);
    }

    private void seedOneSkater(SkaterResponse platform) {
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(nhlPlayer(1, platform.name(), platform.teamAbbrev(), null)));
        when(playerPool.getSkaters()).thenReturn(List.of(platform));
        when(playerPool.getGoalies()).thenReturn(List.of());
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(1)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());
    }

    @Test
    @DisplayName("seeds a goalie the model projects no starts for at nought, with no save % or GAA")
    void seedsGoaliesWithoutWorkloadAtNought() {
        // Left off the board, a third stringer was filled in later from last season's line, and
        // his club's starts came to more than its schedule. His line is the model's nought; the
        // save % and GAA it has no shots for stay out rather than reading as a .000 goalie.
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
        assertThat(seed.players()).extracting(PlayerProjection::getPlayerId).containsExactly(6000, 6001);
        assertThat(seed.players().get(0).getStats().getScoring()).containsEntry("svPct", 0.912);
        assertThat(seed.players().get(1)).satisfies(p -> {
            assertThat(p.getType()).isEqualTo(PlayerProjection.TypeEnum.GOALIE);
            assertThat(p.getStats().getUtility()).containsEntry("gp", 0.0);
            assertThat(p.getStats().getScoring())
                    .containsEntry("gs", 0.0)
                    .containsEntry("w", 0.0)
                    .containsEntry("sv", 0.0)
                    .doesNotContainKeys("svPct", "gaa", "winPct");
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
    @DisplayName("a zeroed row carries every column a played row carries")
    void zeroLineIsAsCompleteAsAPlayedOne() {
        // The zero line exists so a retired player is a complete nought rather than a half-blank
        // row, which means it has to grow whenever the played line does. Comparing the two is
        // what makes that automatic instead of remembered.
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(nhlPlayer(1, "Connor McDavid", "EDM", 97)));
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(fullSkater(1)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());
        when(projectionServiceClient.retiredPlayers())
                .thenReturn(List.of(retiredPlayer(8471685, "Anze Kopitar", "LA", 11)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(
                        platformSkater(5000, "Connor McDavid", "EDM", 97),
                        platformSkater(500, "Anze Kopitar", "LA", 11)));
        when(playerPool.getGoalies()).thenReturn(List.of());

        List<PlayerProjection> players = service.seed(2026, "marcel-v3").players();
        PlayerProjection played = byId(players, 5000);
        PlayerProjection zeroed = byId(players, 500);

        assertThat(zeroed.getStats().getScoring().keySet())
                .containsExactlyInAnyOrderElementsOf(played.getStats().getScoring().keySet());
        assertThat(zeroed.getStats().getUtility().keySet())
                .containsExactlyInAnyOrderElementsOf(played.getStats().getUtility().keySet());
        assertThat(zeroed.getStats().getScoring().values()).allMatch(v -> v == 0.0);
    }

    /** Every column the model emits, so a completeness check is over the whole line. */
    private static SkaterProjectionResponse fullSkater(int nhlId) {
        SkaterProjectionResponse p = skater(nhlId);
        p.setPlusMinus(BigDecimal.valueOf(14));
        p.setPim(BigDecimal.valueOf(28));
        p.setGwGoals(BigDecimal.valueOf(6));
        p.setFaceoffsWon(BigDecimal.valueOf(700));
        p.setFaceoffsLost(BigDecimal.valueOf(650));
        p.setHits(BigDecimal.valueOf(90));
        p.setBlocks(BigDecimal.valueOf(40));
        return p;
    }

    private static PlayerProjection byId(List<PlayerProjection> players, int playerId) {
        return players.stream()
                .filter(p -> p.getPlayerId() == playerId)
                .findFirst()
                .orElseThrow();
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
    @Test
    @DisplayName("a limit returns the top of the board, skaters by points and goalies by wins")
    void limitReturnsTheTop() {
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(
                        nhlPlayer(1, "Low Skater", "TOR", 11),
                        nhlPlayer(2, "High Skater", "TOR", 12),
                        nhlPlayer(3, "Mid Skater", "TOR", 13),
                        nhlPlayer(4, "Low Goalie", "WPG", 31),
                        nhlPlayer(5, "High Goalie", "WPG", 32)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(
                        platformSkater(5001, "Low Skater", "TOR"),
                        platformSkater(5002, "High Skater", "TOR"),
                        platformSkater(5003, "Mid Skater", "TOR")));
        when(playerPool.getGoalies())
                .thenReturn(List.of(
                        platformGoalie(6001, "Low Goalie", "WPG"),
                        platformGoalie(6002, "High Goalie", "WPG")));
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(1, 30), skater(2, 90), skater(3, 60)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString()))
                .thenReturn(List.of(goalie(4, 10), goalie(5, 40)));

        ProjectionSeedService.Seed seed = service.seed(2026, "marcel-v3", 2, 1);

        assertThat(seed.players())
                .extracting(PlayerProjection::getPlayerId)
                .containsExactly(5002, 5003, 6002);
    }

    @Test
    @DisplayName("a limit trims the rows, never the coverage it reports")
    void limitLeavesTheCountsAlone() {
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(
                        nhlPlayer(1, "One", "TOR", 11),
                        nhlPlayer(2, "Two", "TOR", 12),
                        nhlPlayer(3, "Keeper", "WPG", 31)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(platformSkater(5001, "One", "TOR"), platformSkater(5002, "Two", "TOR")));
        when(playerPool.getGoalies()).thenReturn(List.of(platformGoalie(6001, "Keeper", "WPG")));
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(1, 30), skater(2, 90)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString()))
                .thenReturn(List.of(goalie(3, 40)));

        ProjectionSeedService.Seed seed = service.seed(2026, "marcel-v3", 1, null);

        // One skater row asked for, but the preview drawing it still needs to say the model
        // reached two skaters and a goalie.
        assertThat(seed.players()).hasSize(2);
        assertThat(seed.skatersSeeded()).isEqualTo(2);
        assertThat(seed.goaliesSeeded()).isEqualTo(1);
    }

    @Test
    @DisplayName("no limit leaves the board as it was built")
    void noLimitIsUntouched() {
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(nhlPlayer(1, "One", "TOR", 11), nhlPlayer(2, "Two", "TOR", 12)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(platformSkater(5001, "One", "TOR"), platformSkater(5002, "Two", "TOR")));
        when(playerPool.getGoalies()).thenReturn(List.of());
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(1, 30), skater(2, 90)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());

        ProjectionSeedService.Seed seed = service.seed(2026, "marcel-v3");

        assertThat(seed.players())
                .extracting(PlayerProjection::getPlayerId)
                .containsExactly(5001, 5002);
    }

    /**
     * The create page asks for a preview every time the AI starting point is picked, and the
     * limits it passes save no work at all — so the second ask must not repeat a read of every
     * player and every projection the model has.
     */
    @Test
    @DisplayName("a second reader is served the board the first one built")
    void buildsTheBoardOnceAndServesItAgain() {
        stubThreeSkatersAndTwoGoalies();

        service.seed(2026, "marcel-v3", 2, 1);
        ProjectionSeedService.Seed whole = service.seed(2026, "marcel-v3");

        verify(projectionServiceClient, times(1)).activePlayers(any());
        verify(projectionServiceClient, times(1)).skaterProjections(anyInt(), anyString());
        verify(projectionServiceClient, times(1)).goalieProjections(anyInt(), anyString());
        // Cached whole, so the reader that wants all of it still gets all of it.
        assertThat(whole.players())
                .extracting(PlayerProjection::getPlayerId)
                .containsExactly(5001, 5002, 5003, 6001, 6002);
    }

    /** The limits are applied to what the cache hands back, not baked into what it holds. */
    @Test
    @DisplayName("a limited read of a cached board still returns the top of it")
    void limitsApplyToACachedBoard() {
        stubThreeSkatersAndTwoGoalies();

        service.seed(2026, "marcel-v3");
        ProjectionSeedService.Seed top = service.seed(2026, "marcel-v3", 2, 1);

        assertThat(top.players())
                .extracting(PlayerProjection::getPlayerId)
                .containsExactly(5002, 5003, 6002);
        assertThat(top.skatersSeeded()).isEqualTo(3);
        assertThat(top.goaliesSeeded()).isEqualTo(2);
    }

    /** A board that is rebuilt every read is one the model has changed under, not one that hasn't. */
    @Test
    @DisplayName("a different model version is built rather than read from the last one")
    void rebuildsForAnotherModelVersion() {
        stubThreeSkatersAndTwoGoalies();

        service.seed(2026, "marcel-v3");
        service.seed(2026, "marcel-v4");

        verify(projectionServiceClient, times(2)).activePlayers(any());
    }

    private void stubThreeSkatersAndTwoGoalies() {
        when(projectionServiceClient.activePlayers(any()))
                .thenReturn(List.of(
                        nhlPlayer(1, "Low Skater", "TOR", 11),
                        nhlPlayer(2, "High Skater", "TOR", 12),
                        nhlPlayer(3, "Mid Skater", "TOR", 13),
                        nhlPlayer(4, "Low Goalie", "WPG", 31),
                        nhlPlayer(5, "High Goalie", "WPG", 32)));
        when(playerPool.getSkaters())
                .thenReturn(List.of(
                        platformSkater(5001, "Low Skater", "TOR"),
                        platformSkater(5002, "High Skater", "TOR"),
                        platformSkater(5003, "Mid Skater", "TOR")));
        when(playerPool.getGoalies())
                .thenReturn(List.of(
                        platformGoalie(6001, "Low Goalie", "WPG"),
                        platformGoalie(6002, "High Goalie", "WPG")));
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(skater(1, 30), skater(2, 90), skater(3, 60)));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString()))
                .thenReturn(List.of(goalie(4, 10), goalie(5, 40)));
    }
}
