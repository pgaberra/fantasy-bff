package com.fantasy.bff;

import com.fantasy.bff.service.EspnPlayerPoolSource;
import com.fantasy.bff.service.PlayerPoolSource;
import com.fantasy.bff.service.YahooPlayerPoolSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exactly one pool source is wired in, and which one is a configuration choice. Both halves are
 * asserted because the switch is a deployment step: an environment that sets the flag has to
 * come up on ESPN, and every environment that doesn't has to stay on Yahoo.
 */
class PlayerSourceWiringTest {

    @Nested
    @SpringBootTest
    class ByDefault extends BaseIntegrationTest {

        @Autowired
        private PlayerPoolSource playerPool;

        @Test
        void thePoolComesFromYahoo() {
            assertThat(playerPool).isInstanceOf(YahooPlayerPoolSource.class);
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = "players.source=espn")
    class WhenSwitchedToEspn extends BaseIntegrationTest {

        @Autowired
        private PlayerPoolSource playerPool;

        @Test
        void thePoolComesFromEspn() {
            assertThat(playerPool).isInstanceOf(EspnPlayerPoolSource.class);
        }
    }
}
