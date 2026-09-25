package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EspnPoolIdCrosswalkTest {

    private static final int STATS_SEASON = 2025;

    private final PlayerPoolSource pool = mock(PlayerPoolSource.class);
    private final EspnServiceClient espn = mock(EspnServiceClient.class);

    private EspnPoolIdCrosswalk crosswalk(long ttlMs) {
        return new EspnPoolIdCrosswalk(pool, espn, new PlayerIdResolver(), STATS_SEASON, ttlMs);
    }

    private static com.fantasy.bff.generated.espn.model.SkaterResponse espnSkater(
            long id, String first, String last, String team, Integer sweater) {
        return new com.fantasy.bff.generated.espn.model.SkaterResponse()
                .id(id).firstName(first).lastName(last).teamAbbrev(team).sweaterNumber(sweater);
    }

    private static SkaterResponse poolSkater(int id, String name, String team, Integer sweater) {
        return new SkaterResponse(id, name, team, null, sweater, Set.of(), null);
    }

    private void yahooPool() {
        when(pool.playerIdSpace()).thenReturn(PlayerIdSpace.YAHOO);
        when(espn.skaters(STATS_SEASON)).thenReturn(List.of(
                espnSkater(4001, "Connor", "McDavid", "EDM", 97),
                espnSkater(4002, "Nobody", "Known", "EDM", 12)));
        when(espn.goalies(STATS_SEASON)).thenReturn(List.of(
                new com.fantasy.bff.generated.espn.model.GoalieResponse()
                        .id(4003L).firstName("Stuart").lastName("Skinner").teamAbbrev("EDM").sweaterNumber(74)));
        when(pool.getSkaters()).thenReturn(List.of(poolSkater(6743, "Connor McDavid", "EDM", 97)));
        when(pool.getGoalies()).thenReturn(List.of(new GoalieResponse(7109, "Stuart Skinner", "EDM", null, 74, null)));
    }

    @Test
    void mapsEspnIdsOntoAYahooPoolByIdentity() {
        yahooPool();

        Map<Long, Integer> poolIds = crosswalk(60_000).poolIds(List.of(4001L, 4002L, 4003L));

        assertThat(poolIds).containsExactlyInAnyOrderEntriesOf(Map.of(4001L, 6743, 4003L, 7109));
    }

    @Test
    void buildsTheCrosswalkOnceWhileItIsFresh() {
        yahooPool();
        EspnPoolIdCrosswalk crosswalk = crosswalk(60_000);

        crosswalk.poolIds(List.of(4001L));
        crosswalk.poolIds(List.of(4003L));

        verify(espn, times(1)).skaters(STATS_SEASON);
        verify(pool, times(1)).getSkaters();
    }

    @Test
    void buildsItAgainOnceItHasExpired() {
        yahooPool();
        EspnPoolIdCrosswalk crosswalk = crosswalk(0);

        crosswalk.poolIds(List.of(4001L));
        crosswalk.poolIds(List.of(4001L));

        verify(espn, times(2)).skaters(STATS_SEASON);
    }

    @Test
    void passesEspnIdsStraightThroughOverAnEspnPool() {
        when(pool.playerIdSpace()).thenReturn(PlayerIdSpace.ESPN);

        assertThat(crosswalk(60_000).poolIds(List.of(4001L))).containsExactlyEntriesOf(Map.of(4001L, 4001));
        verifyNoInteractions(espn);
    }

    @Test
    void readsNothingForNoPicks() {
        when(pool.playerIdSpace()).thenReturn(PlayerIdSpace.YAHOO);

        assertThat(crosswalk(60_000).poolIds(List.of())).isEmpty();
        verifyNoInteractions(espn);
    }
}
