package com.fantasy.bff.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rounding has to be the browser's, because a board is scored on the numbers as they are
 * written and the browser writes them. The golden vectors cover the ordinary cases; these are the
 * two places a plain {@code setScale(HALF_UP)} parts company with {@code toFixed}, which no
 * fixture is likely to hold by chance.
 */
class JsNumbersTest {

    @Test
    @DisplayName("rounds the double's real value, not the decimal Java prints for it")
    void roundsTheBinaryValue() {
        // 1.005 is really 1.00499999999999989, which is why the browser gives "1.00" here.
        assertThat(JsNumbers.toFixed(1.005, 2)).isEqualTo(1.0);
        assertThat(JsNumbers.toFixed(1.015, 2)).isEqualTo(1.01);
        // And 2.675 is really 2.67499999999999982.
        assertThat(JsNumbers.toFixed(2.675, 2)).isEqualTo(2.67);
    }

    @Test
    @DisplayName("takes the larger number on an exact half, which is not away from zero")
    void roundsHalvesTowardsTheLargerNumber() {
        assertThat(JsNumbers.toFixed(2.5, 0)).isEqualTo(3);
        assertThat(JsNumbers.toFixed(0.5, 0)).isEqualTo(1);
        // Java's HALF_UP would give -2 here; ECMA-262 says to pick the larger candidate.
        assertThat(JsNumbers.toFixed(-1.5, 0)).isEqualTo(-1);
        assertThat(JsNumbers.toFixed(-2.5, 0)).isEqualTo(-2);
        assertThat(JsNumbers.toFixed(-0.25, 1)).isEqualTo(-0.2);
    }

    @Test
    @DisplayName("keeps whole numbers and ordinary lines exactly as they are")
    void leavesOrdinaryNumbersAlone() {
        assertThat(JsNumbers.toFixed(49.4, 1)).isEqualTo(49.4);
        assertThat(JsNumbers.toFixed(49.44, 1)).isEqualTo(49.4);
        assertThat(JsNumbers.toFixed(49.45, 1)).isEqualTo(49.5);
        assertThat(JsNumbers.toFixed(62, 0)).isEqualTo(62);
        assertThat(JsNumbers.toFixed(0.9205, 3)).isEqualTo(0.92);
    }

    @Test
    @DisplayName("passes a number that cannot be rounded straight through")
    void passesNonFiniteThrough() {
        assertThat(JsNumbers.toFixed(Double.NaN, 2)).isNaN();
        assertThat(JsNumbers.toFixed(Double.POSITIVE_INFINITY, 2)).isInfinite();
        // toFixed takes no negative places; neither does this.
        assertThat(JsNumbers.toFixed(12.7, -1)).isEqualTo(13);
    }
}
