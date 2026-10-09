package com.fantasy.bff.service;

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
    void isOnOverAYahooPool() {
        assertThat(new LeagueDraftSyncAvailability(pool(PlayerIdSpace.YAHOO)).available()).isTrue();
    }

    @Test
    void isOffOverAnEspnPoolSinceThePicksNameYahooIds() {
        assertThat(new LeagueDraftSyncAvailability(pool(PlayerIdSpace.ESPN)).available()).isFalse();
    }
}
