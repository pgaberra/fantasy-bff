package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.request.GameRange;
import com.fantasy.bff.dto.response.PlayerSplitResponse;
import com.fantasy.bff.generated.projection.model.GoalieSplitResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterSplitResponse;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
class PlayerSplitServiceTest {

    private static final int NHL_ID = 8478402;
    private static final int FORWARD_ID = 77;
    private static final int DEFENCEMAN_ID = 88;
    private static final int SEASON = 2025;
    private static final GameRange LAST_TWENTY = GameRange.ofLastGames(20);

    @Mock private ProjectionServiceClient projectionServiceClient;
    @Mock private PlayerSplitContextProvider contextProvider;

    private PlayerSplitService service;

    @BeforeEach
    void setUp() {
        service = new PlayerSplitService(projectionServiceClient, contextProvider);
        givenPlatformPlayer(FORWARD_ID, Set.of());
    }

    private void givenPlatformPlayer(int platformId, Set<Integer> defenceEligible) {
        PlayerResponse identity = new PlayerResponse();
        identity.setNhlId(NHL_ID);
        identity.setFullName("Connor McDavid");
        identity.setCurrentTeam("EDM");
        when(contextProvider.context()).thenReturn(new PlayerSplitContextProvider.Context(
                new PlayerIdMapping(Map.of((long) NHL_ID, platformId), List.of(), 1, 0, 0),
                Map.of((long) NHL_ID, identity),
                defenceEligible,
                Optional.empty(),
                List.of(),
                Map.of()));
    }

    private Map<String, Double> skaterStats() {
        return service.skaterSplits(SEASON, LAST_TWENTY, 100).get(0).stats();
    }

    @Test
    @DisplayName("sums power play and shorthanded into the one special-teams category leagues score")
    void derivesSpecialTeams() {
        when(projectionServiceClient.skaterSplits(anyInt(), any(), anyInt()))
                .thenReturn(List.of(skater()));

        Map<String, Double> stats = skaterStats();

        // 7 pp goals + 2 sh goals; 18 pp points - 7 goals = 11 pp assists, 5 sh points - 2 = 3.
        assertThat(stats).containsEntry("stpg", 9.0)
                .containsEntry("stpa", 14.0)
                .containsEntry("stp", 23.0);
    }

    @Test
    @DisplayName("passes through the stats only the per-game rows can answer")
    void carriesTheGameLevelStats() {
        when(projectionServiceClient.skaterSplits(anyInt(), any(), anyInt()))
                .thenReturn(List.of(skater()));

        Map<String, Double> stats = skaterStats();

        assertThat(stats).containsEntry("hatTricks", 2.0)
                .containsEntry("shifts", 480.0)
                .containsEntry("toi", 24000.0);
    }

    @Test
    @DisplayName("scores defencemen points for a defenceman and not at all for a forward")
    void countsDefencePointsOnlyForDefenceEligiblePlayers() {
        when(projectionServiceClient.skaterSplits(anyInt(), any(), anyInt()))
                .thenReturn(List.of(skater()));

        assertThat(skaterStats()).doesNotContainKey("defPoints");

        givenPlatformPlayer(DEFENCEMAN_ID, Set.of(DEFENCEMAN_ID));

        assertThat(skaterStats()).containsEntry("defPoints", 42.0);
    }

    @Test
    @DisplayName("charges an overtime loss to the goalie and counts it as a decision")
    void derivesGoalieOvertimeStats() {
        when(projectionServiceClient.goalieSplits(anyInt(), any(), anyInt()))
                .thenReturn(List.of(goalie(9, 4, 3)));

        Map<String, Double> stats = service.goalieSplits(SEASON, LAST_TWENTY, 100).get(0).stats();

        assertThat(stats).containsEntry("otl", 3.0).containsEntry("toi", 57600.0);
        // 9 of 16 decisions, not 9 of the 13 that would be left if overtime losses vanished.
        assertThat(stats.get("winPct")).isCloseTo(0.563, within(0.0005));
    }

    @Test
    @DisplayName("leaves win percentage out for a goalie with no decisions rather than calling it zero")
    void omitsWinPctWithoutDecisions() {
        when(projectionServiceClient.goalieSplits(anyInt(), any(), anyInt()))
                .thenReturn(List.of(goalie(0, 0, 0)));

        assertThat(service.goalieSplits(SEASON, LAST_TWENTY, 100).get(0).stats())
                .doesNotContainKey("winPct");
    }

    @Test
    @DisplayName("drops a split whose NHL id the platform doesn't carry")
    void skipsUnmappedPlayers() {
        when(contextProvider.context()).thenReturn(new PlayerSplitContextProvider.Context(
                new PlayerIdMapping(Map.of(), List.of(), 0, 0, 0), Map.of(), Set.of(),
                Optional.empty(),
                List.of(),
                Map.of()));
        when(projectionServiceClient.skaterSplits(anyInt(), any(), anyInt()))
                .thenReturn(List.of(skater()));

        List<PlayerSplitResponse> splits = service.skaterSplits(SEASON, LAST_TWENTY, 100);

        assertThat(splits).isEmpty();
    }

    private static SkaterSplitResponse skater() {
        SkaterSplitResponse split = new SkaterSplitResponse();
        split.setNhlId(NHL_ID);
        split.setGames(20);
        split.setGoals(15);
        split.setAssists(27);
        split.setPoints(42);
        split.setPpGoals(7);
        split.setPpPoints(18);
        split.setShGoals(2);
        split.setShPoints(5);
        split.setShots(80);
        split.setShifts(480);
        split.setToiSeconds(24000);
        split.setHatTricks(2);
        return split;
    }

    private static GoalieSplitResponse goalie(int wins, int losses, int otLosses) {
        GoalieSplitResponse split = new GoalieSplitResponse();
        split.setNhlId(NHL_ID);
        split.setGames(16);
        split.setGamesStarted(16);
        split.setWins(wins);
        split.setLosses(losses);
        split.setOtLosses(otLosses);
        split.setShutouts(2);
        split.setShotsAgainst(480);
        split.setGoalsAgainst(40);
        split.setSaves(440);
        split.setSavePct(BigDecimal.valueOf(0.917));
        split.setToiSeconds(57600);
        return split;
    }
}
