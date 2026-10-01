package com.fantasy.bff.service;

import com.fantasy.bff.config.WhosHotAvailableFilterProperties;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WhosHotAvailableFilterAvailabilityTest {

    private static PlayerPoolSource pool(PlayerIdSpace space) {
        PlayerPoolSource pool = mock(PlayerPoolSource.class);
        when(pool.playerIdSpace()).thenReturn(space);
        return pool;
    }

    private static WhosHotAvailableFilterAvailability availability(Boolean enabled, PlayerIdSpace space) {
        return new WhosHotAvailableFilterAvailability(new WhosHotAvailableFilterProperties(enabled), pool(space));
    }

    @Test
    void isOffUnlessConfigured() {
        assertThat(new WhosHotAvailableFilterProperties(null).enabled()).isFalse();
        assertThat(availability(null, PlayerIdSpace.YAHOO).available()).isFalse();
        assertThatThrownBy(() -> availability(null, PlayerIdSpace.YAHOO).require())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void isOnWhenEnabledOverAYahooPool() {
        assertThat(availability(true, PlayerIdSpace.YAHOO).available()).isTrue();
    }

    @Test
    void staysOffOverAnEspnPoolSinceAYahooRosterNamesYahooIds() {
        assertThat(availability(true, PlayerIdSpace.ESPN).available()).isFalse();
    }
}
