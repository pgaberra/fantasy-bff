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
        assertThat(new LeagueDraftSyncProperties(null).enabled()).isFalse();
        assertThat(new LeagueDraftSyncAvailability(new LeagueDraftSyncProperties(null), pool(PlayerIdSpace.YAHOO))
                .available()).isFalse();
    }

    @Test
    void isOnWhenEnabledOverAYahooPool() {
        assertThat(new LeagueDraftSyncAvailability(new LeagueDraftSyncProperties(true), pool(PlayerIdSpace.YAHOO))
                .available()).isTrue();
    }

    @Test
    void staysOffOverAnEspnPoolSinceThePicksNameYahooIds() {
        assertThat(new LeagueDraftSyncAvailability(new LeagueDraftSyncProperties(true), pool(PlayerIdSpace.ESPN))
                .available()).isFalse();
    }
}
