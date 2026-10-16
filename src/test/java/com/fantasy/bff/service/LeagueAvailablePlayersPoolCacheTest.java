package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.PlayerServiceClient.RosteredPlayer;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.PlannerRosterPlayer;
import com.fantasy.bff.generated.espn.model.SkaterResponse;
import com.fantasy.bff.generated.yahoo.model.YahooAvailablePlayerResponse;
import com.fantasy.bff.service.LeagueAvailablePlayers.Available;
import com.fantasy.bff.service.LeagueAvailablePlayers.Wire;
import com.fantasy.bff.service.LeagueOwnTeam.LeagueTeam;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A platform's pool and its join are held between reads; a league's rosters and waivers are not.
 */
class LeagueAvailablePlayersPoolCacheTest {

    private static final long HELD = 900_000;

    /** One rival team holding nobody: the league has rosters, so the whole pool is available. */
    private static final List<LeagueTeam> NOBODY_HELD = List.of(new LeagueTeam("Rival", false, List.of()));

    private final YahooServiceClient yahoo = mock(YahooServiceClient.class);
    private final EspnServiceClient espn = mock(EspnServiceClient.class);
    private final PlayerServiceClient playerService = mock(PlayerServiceClient.class);
    private final NhlIdentityJoin identityJoin = mock(NhlIdentityJoin.class);

    LeagueAvailablePlayersPoolCacheTest() {
        when(playerService.getRosteredPlayers()).thenReturn(List.of(
                new RosteredPlayer("1", "Held Star", "SJ", List.of("C"), false, 19, 99.0),
                new RosteredPlayer("2", "William Eklund", "SJ", List.of("LW"), false, 72, 3.0)));
        when(espn.skaters(anyInt())).thenReturn(List.of(
                new SkaterResponse().id(1L).firstName("Held").lastName("Star").teamAbbrev("SJ")));
        when(espn.goalies(anyInt())).thenReturn(List.of());
        when(yahoo.leagueWaivers(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(espn.leagueWaivers(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(identityJoin.join(anyList())).thenReturn(new PlayerIdMapping(Map.of(), List.of(), 0, 0, 0));
    }

    @Test
    void aSecondReadWithinTheWindowMakesNoPoolCallAndNoJoin() {
        LeagueAvailablePlayers players = players(HELD);

        players.read("user-1", PlayerIdSpace.YAHOO, "league-a", NOBODY_HELD);
        Wire wire = players.read("user-2", PlayerIdSpace.YAHOO, "league-b", NOBODY_HELD);

        assertThat(wire.players()).extracting(Available::playerId).containsExactly("1", "2");
        verify(playerService, times(1)).getRosteredPlayers();
        verify(identityJoin, times(1)).join(anyList());
        // The waivers are the league's own, so they are asked every time.
        verify(yahoo, times(2)).leagueWaivers(anyString(), anyString(), anyInt());
    }

    @Test
    void anEspnPoolIsHeldTheSameWay() {
        LeagueAvailablePlayers players = players(HELD);

        players.read("user-1", PlayerIdSpace.ESPN, "1", NOBODY_HELD);
        players.read("user-1", PlayerIdSpace.ESPN, "2", NOBODY_HELD);

        verify(espn, times(1)).skaters(anyInt());
        verify(espn, times(1)).goalies(anyInt());
        verify(identityJoin, times(1)).join(anyList());
        verify(espn, times(2)).leagueWaivers(anyString(), anyString(), anyInt());
    }

    @Test
    void readsArrivingDuringARefreshWaitForItInsteadOfReadingThePoolThemselves() throws Exception {
        CountDownLatch refreshing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<RosteredPlayer> pool = playerService.getRosteredPlayers();
        when(playerService.getRosteredPlayers()).thenAnswer(invocation -> {
            refreshing.countDown();
            release.await(5, TimeUnit.SECONDS);
            return pool;
        });
        LeagueAvailablePlayers players = players(HELD);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<Wire> first = threads.submit(() -> players.read("user-1", PlayerIdSpace.YAHOO, "a", NOBODY_HELD));
            assertThat(refreshing.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Wire> second = threads.submit(() -> players.read("user-2", PlayerIdSpace.YAHOO, "b", NOBODY_HELD));
            release.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).players()).hasSize(2);
            assertThat(second.get(5, TimeUnit.SECONDS).players()).hasSize(2);
        } finally {
            threads.shutdownNow();
        }
        // The stub was called once above to capture the pool, and once by the refresh.
        verify(playerService, times(2)).getRosteredPlayers();
        verify(identityJoin, times(1)).join(anyList());
    }

    @Test
    void eachPlatformHoldsItsOwnPool() {
        LeagueAvailablePlayers players = players(HELD);

        players.read("user-1", PlayerIdSpace.YAHOO, "league-a", NOBODY_HELD);
        players.read("user-1", PlayerIdSpace.ESPN, "1", NOBODY_HELD);

        verify(playerService, times(1)).getRosteredPlayers();
        verify(espn, times(1)).skaters(anyInt());
        verify(identityJoin, times(2)).join(anyList());
    }

    @Test
    void anExpiredPoolIsReadAndJoinedAgain() {
        LeagueAvailablePlayers players = players(0);

        players.read("user-1", PlayerIdSpace.YAHOO, "league-a", NOBODY_HELD);
        players.read("user-1", PlayerIdSpace.YAHOO, "league-a", NOBODY_HELD);

        verify(playerService, times(2)).getRosteredPlayers();
        verify(identityJoin, times(2)).join(anyList());
    }

    @Test
    void eachLeaguesRostersStillLeaveTheWireWhenThePoolIsHeld() {
        LeagueAvailablePlayers players = players(HELD);
        players.read("user-1", PlayerIdSpace.YAHOO, "league-a", NOBODY_HELD);

        Wire wire = players.read("user-1", PlayerIdSpace.YAHOO, "league-b",
                List.of(new LeagueTeam("Rival", false, List.of(rostered("1")))));

        assertThat(wire.players()).extracting(Available::playerId).containsExactly("2");
    }

    @SuppressWarnings("unchecked")
    @Test
    void aWaiverRowThePoolLacksIsJoinedWithTheWholePoolAroundIt() {
        LeagueAvailablePlayers players = players(HELD);
        players.read("user-1", PlayerIdSpace.YAHOO, "league-a", NOBODY_HELD);
        when(yahoo.leagueWaivers(anyString(), anyString(), anyInt())).thenReturn(List.of(
                new YahooAvailablePlayerResponse()
                        .yahooId("9")
                        .fullName("Fresh Call-Up")
                        .teamAbbrev("SJ")
                        .eligiblePositions(List.of("C"))
                        .goalie(false)
                        .availability(YahooAvailablePlayerResponse.AvailabilityEnum.WAIVERS)));

        Wire wire = players.read("user-1", PlayerIdSpace.YAHOO, "league-a", NOBODY_HELD);

        assertThat(wire.players()).extracting(Available::playerId).containsExactly("1", "2", "9");
        ArgumentCaptor<List<NhlIdentityJoin.PlatformPlayer>> joined = ArgumentCaptor.forClass(List.class);
        verify(identityJoin, times(2)).join(joined.capture());
        assertThat(joined.getValue()).extracting(NhlIdentityJoin.PlatformPlayer::playerId)
                .containsExactly("1", "2", "9");
        verify(playerService, times(1)).getRosteredPlayers();
    }

    @Test
    void aFailedRefreshServesThePreviousPool() {
        LeagueAvailablePlayers players = players(0);
        players.read("user-1", PlayerIdSpace.YAHOO, "league-a", NOBODY_HELD);
        when(playerService.getRosteredPlayers()).thenThrow(new IllegalStateException("yahoo-service is down"));

        Wire wire = players.read("user-1", PlayerIdSpace.YAHOO, "league-a", NOBODY_HELD);

        assertThat(wire.players()).extracting(Available::playerId).containsExactly("1", "2");
    }

    @Test
    void aFailedFirstReadFails() {
        when(playerService.getRosteredPlayers()).thenThrow(new IllegalStateException("yahoo-service is down"));
        LeagueAvailablePlayers players = players(HELD);

        assertThatThrownBy(() -> players.read("user-1", PlayerIdSpace.YAHOO, "league-a", NOBODY_HELD))
                .isInstanceOf(IllegalStateException.class);
    }

    private LeagueAvailablePlayers players(long poolTtlMs) {
        return new LeagueAvailablePlayers(
                yahoo, espn, playerService, mock(LeagueOwnTeam.class), identityJoin, 2025, poolTtlMs);
    }

    private static LeagueOwnTeam.Rostered rostered(String playerId) {
        return new LeagueOwnTeam.Rostered(
                new PlannerRosterPlayer(playerId, "Held Star", "SJ", "skater", List.of("C"), null, null, false),
                19,
                List.of());
    }
}
