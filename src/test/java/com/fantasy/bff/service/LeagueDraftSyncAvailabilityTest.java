package com.fantasy.bff.service;

import com.fantasy.bff.config.LeagueDraftSyncProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeagueDraftSyncAvailabilityTest {

    private static PlayerPoolSource pool(PlayerIdSpace space) {
        PlayerPoolSource pool = mock(PlayerPoolSource.class);
        when(pool.playerIdSpace()).thenReturn(space);
        return pool;
    }

    @Test
    void isOffUnlessConfigured() {
        assertThat(new LeagueDraftSyncProperties(null, null).enabled()).isFalse();
        assertThat(new LeagueDraftSyncAvailability(new LeagueDraftSyncProperties(null, null), pool(PlayerIdSpace.YAHOO))
                .available()).isFalse();
    }

    @Test
    void isOnWhenEnabledOverAYahooPool() {
        assertThat(new LeagueDraftSyncAvailability(new LeagueDraftSyncProperties(true, null), pool(PlayerIdSpace.YAHOO))
                .available()).isTrue();
    }

    @Test
    void staysOffOverAnEspnPoolSinceThePicksNameYahooIds() {
        assertThat(new LeagueDraftSyncAvailability(new LeagueDraftSyncProperties(true, null), pool(PlayerIdSpace.ESPN))
                .available()).isFalse();
    }

    @Test
    void espnIsOffUnlessConfiguredWhateverYahooSays() {
        assertThat(new LeagueDraftSyncAvailability(new LeagueDraftSyncProperties(true, null), pool(PlayerIdSpace.YAHOO))
                .espnAvailable()).isFalse();
    }

    @Test
    void espnIsOnWhenEnabledOverEitherPoolSinceItsPicksAreMapped() {
        assertThat(new LeagueDraftSyncAvailability(new LeagueDraftSyncProperties(null, true), pool(PlayerIdSpace.YAHOO))
                .espnAvailable()).isTrue();
        assertThat(new LeagueDraftSyncAvailability(new LeagueDraftSyncProperties(null, true), pool(PlayerIdSpace.ESPN))
                .espnAvailable()).isTrue();
    }
}
