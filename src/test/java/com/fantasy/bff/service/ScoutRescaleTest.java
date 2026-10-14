package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasy.bff.dto.response.ScoutLine;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An earlier line brought onto today's games left: its share of the schedule, at its own rates. */
class ScoutRescaleTest {

    @Test
    void reSpreadsTheTotalsOverTheShareOfTodaysGamesItExpectedAndKeepsTheRates() {
        // 80 of 84 games expected, 40 goals, 20 minutes a night, 10% shooting.
        ScoutLine then = new ScoutLine(80, Map.of(
                "goals", 40.0, "toi", 96000.0, "toiPerGame", 1200.0, "shPct", 10.0));

        ScoutLine rescaled = ScoutRescale.toGamesLeft(then, 84, 42);

        // Half the schedule left, so 40 of the 42 games and half the counting totals.
        assertThat(rescaled.games()).isCloseTo(40.0, within(1e-9));
        assertThat(rescaled.stats().get("goals")).isCloseTo(20.0, within(1e-9));
        assertThat(rescaled.stats().get("toi")).isCloseTo(48000.0, within(1e-9));
        assertThat(rescaled.stats().get("toiPerGame")).isEqualTo(1200.0);
        assertThat(rescaled.stats().get("shPct")).isEqualTo(10.0);
    }

    @Test
    void aGoaliesStartsAreTheGamesAndHisRatesCarry() {
        ScoutLine then = new ScoutLine(50, Map.of("gs", 50.0, "w", 25.0, "svPct", 0.91, "gaa", 2.6, "winPct", 0.5));

        ScoutLine rescaled = ScoutRescale.toGamesLeft(then, 73, 78);

        assertThat(rescaled.games()).isCloseTo(53.4247, within(1e-4));
        assertThat(rescaled.stats().get("gs")).isCloseTo(53.4247, within(1e-4));
        assertThat(rescaled.stats().get("w")).isCloseTo(26.7123, within(1e-4));
        assertThat(rescaled.stats().get("svPct")).isEqualTo(0.91);
        assertThat(rescaled.stats().get("gaa")).isEqualTo(2.6);
        assertThat(rescaled.stats().get("winPct")).isEqualTo(0.5);
    }

    @Test
    void aLineWithNoGamesOrNoScheduleBehindItHasNoRateToCarry() {
        ScoutLine empty = new ScoutLine(0, Map.of("goals", 0.0));

        assertThat(ScoutRescale.toGamesLeft(empty, 84, 42)).isNull();
        assertThat(ScoutRescale.toGamesLeft(new ScoutLine(10, Map.of("goals", 5.0)), 0, 42)).isNull();
        assertThat(ScoutRescale.toGamesLeft(null, 84, 42)).isNull();
    }

    @Test
    void noGamesLeftIsAnEmptyLineRatherThanNone() {
        ScoutLine rescaled = ScoutRescale.toGamesLeft(new ScoutLine(10, Map.of("goals", 5.0)), 20, 0);

        assertThat(rescaled.games()).isZero();
        assertThat(rescaled.stats().get("goals")).isZero();
    }
}
