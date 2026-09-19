package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.service.mapping.PlayerIdOverrides;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
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
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerSplitContextProviderTest {

    private static final long HALF_AN_HOUR_MS = 1_800_000;
    private static final long ALREADY_EXPIRED = 0;
    private static final int MCDAVID_NHL_ID = 8478402;
    private static final int MCDAVID_PLATFORM_ID = 77;

    private static final int SEASON = 2026;

    @Mock private ProjectionServiceClient projectionServiceClient;
    @Mock private PlayerPoolSource playerPool;

    @BeforeEach
    void setUp() {
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(nhlMcDavid()));
        when(playerPool.getSkaters()).thenReturn(List.of(platformMcDavid()));
        when(playerPool.getGoalies()).thenReturn(List.of());
    }

    private PlayerSplitContextProvider provider(long ttlMs) {
        return new PlayerSplitContextProvider(
                projectionServiceClient,
                playerPool,
                new PlayerIdResolver(),
                new PlayerIdOverrides(""),
                ttlMs,
                SEASON);
    }

    @Test
    @DisplayName("matches the two sides so a split can be attributed to a platform player")
    void resolvesTheMapping() {
        PlayerSplitContextProvider.Context context = provider(HALF_AN_HOUR_MS).context();

        assertThat(context.platformId(MCDAVID_NHL_ID)).isEqualTo(MCDAVID_PLATFORM_ID);
    }

    @Test
    @DisplayName("reads both sides once, not once per request")
    void cachesWhileFresh() {
        PlayerSplitContextProvider provider = provider(HALF_AN_HOUR_MS);

        provider.context();
        provider.context();
        provider.context();

        verify(projectionServiceClient, times(1)).activePlayers(any());
        verify(playerPool, times(1)).getSkaters();
        verify(playerPool, times(1)).getGoalies();
    }

    @Test
    @DisplayName("rebuilds the mapping once it has expired, so a traded player still matches")
    void refreshesAfterTtl() {
        PlayerSplitContextProvider provider = provider(ALREADY_EXPIRED);

        provider.context();
        provider.context();

        verify(projectionServiceClient, times(2)).activePlayers(any());
    }

    @Test
    @DisplayName("serves the previous mapping when a refresh fails")
    void survivesAFailedRefresh() {
        PlayerSplitContextProvider provider = provider(ALREADY_EXPIRED);
        provider.context();
        upstreamStartsTimingOut();

        PlayerSplitContextProvider.Context context = provider.context();

        assertThat(context.platformId(MCDAVID_NHL_ID)).isEqualTo(MCDAVID_PLATFORM_ID);
    }

    @Test
    @DisplayName("backs off after a failed refresh instead of timing out on every request")
    void holdsOffRetryingAFailure() {
        PlayerSplitContextProvider provider = provider(ALREADY_EXPIRED);
        provider.context();
        upstreamStartsTimingOut();
        provider.context();

        provider.context();
        provider.context();

        // One successful load, one failed attempt, and then nothing: the requests after the
        // failure are served from the stale mapping rather than each paying the timeout again.
        verify(projectionServiceClient, times(2)).activePlayers(any());
    }

    @Test
    @DisplayName("surfaces the failure when there is no mapping to fall back on")
    void failsWhenNothingIsCached() {
        upstreamStartsTimingOut();

        // IllegalStateException is what the handler turns into a 502, the same answer the
        // caller would have got before anything was cached.
        assertThatThrownBy(() -> provider(HALF_AN_HOUR_MS).context())
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(ResourceAccessException.class);
    }

    /**
     * Rookie status rides along on the same identities, and the projection service withholds it
     * wholesale when its history is too shallow — so a single player without it leaves the whole
     * answer undecided rather than reading as "that one is not a rookie".
     */
    @Test
    @DisplayName("carries rookie status across, and withholds it when any player lacks it")
    void resolvesRookies() {
        PlayerResponse rookie = nhlMcDavid();
        rookie.setRookie(true);
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(rookie));

        assertThat(provider(HALF_AN_HOUR_MS).context().rookies())
                .contains(Set.of(MCDAVID_PLATFORM_ID));

        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(nhlMcDavid()));
        assertThat(provider(HALF_AN_HOUR_MS).context().rookies()).isEmpty();
    }

    @Test
    @DisplayName("asks for the season being projected, since rookie status is relative to one")
    void asksAboutTheProjectedSeason() {
        provider(HALF_AN_HOUR_MS).context();

        verify(projectionServiceClient).activePlayers(SEASON);
    }

    private void upstreamStartsTimingOut() {
        when(projectionServiceClient.activePlayers(any()))
                .thenThrow(new ResourceAccessException("projection-service timed out"));
    }

    private static PlayerResponse nhlMcDavid() {
        PlayerResponse player = new PlayerResponse();
        player.setNhlId(MCDAVID_NHL_ID);
        player.setFullName("Connor McDavid");
        player.setCurrentTeam("EDM");
        player.setSweaterNumber(97);
        player.setIsActive(true);
        return player;
    }

    private static SkaterResponse platformMcDavid() {
        return new SkaterResponse(
                MCDAVID_PLATFORM_ID, "Connor McDavid", "EDM", null, 97, Set.of(), null);
    }
}
