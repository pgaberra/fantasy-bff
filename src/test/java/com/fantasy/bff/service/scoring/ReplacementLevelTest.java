package com.fantasy.bff.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasy.bff.dto.response.RosterSlots;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReplacementLevelTest {

    private final Map<Integer, Double> values = new HashMap<>();
    private final Map<Integer, Set<String>> positions = new HashMap<>();

    private void player(int id, double value, String... at) {
        values.put(id, value);
        positions.put(id, Set.of(at));
    }

    /** c, lw, rw, w, f, d, util, bn, g. */
    private static RosterSlots slots(int c, int lw, int rw, int d, int util, int g) {
        return new RosterSlots(c, lw, rw, 0, 0, d, util, 0, g);
    }

    @Test
    void aLeagueThatStartsMoreDefencemenRanksItsBestDefencemanWithItsBestForwards() {
        player(1, 100, "C");
        player(2, 90, "C");
        player(3, 80, "C");
        player(11, 50, "D");
        player(12, 45, "D");
        player(13, 40, "D");
        player(14, 35, "D");
        player(15, 30, "D");

        Map<Integer, ReplacementLevel.Valued> valued = ReplacementLevel.value(values, positions, slots(1, 0, 0, 2, 0, 0), 2);

        assertThat(valued.get(1).overReplacement()).isCloseTo(20, within(1e-9));
        assertThat(valued.get(2).overReplacement()).isCloseTo(10, within(1e-9));
        assertThat(valued.get(11).overReplacement()).as("half the points, but twice the second centre over his replacement")
                .isCloseTo(20, within(1e-9));
        assertThat(valued.get(12).overReplacement()).isGreaterThan(valued.get(2).overReplacement());
        assertThat(valued.get(11).value()).isEqualTo(50);
        assertThat(valued.get(11).positionRank()).isEqualTo("D1");
        assertThat(valued.get(13).positionRank()).isEqualTo("D3");
    }

    @Test
    void utilIsFilledFromTheBestLeftoversSoItDeepensTheForwards() {
        player(1, 100, "C");
        player(2, 90, "C");
        player(3, 30, "C");
        player(11, 50, "D");
        player(12, 40, "D");

        Map<Integer, ReplacementLevel.Valued> valued = ReplacementLevel.value(values, positions, slots(1, 0, 0, 1, 1, 0), 1);

        assertThat(valued.get(2).overReplacement()).as("the second centre starts at Util, so the third replaces")
                .isCloseTo(60, within(1e-9));
        assertThat(valued.get(11).overReplacement()).isCloseTo(10, within(1e-9));
    }

    @Test
    void aPlayerOfTwoPositionsIsMeasuredWhereHisReplacementIsWeakest() {
        player(1, 100, "C");
        player(2, 95, "C");
        player(3, 90, "C");
        player(21, 80, "LW");
        player(22, 40, "LW");
        player(30, 85, "C", "LW");

        Map<Integer, ReplacementLevel.Valued> valued = ReplacementLevel.value(values, positions, slots(1, 1, 0, 0, 0, 0), 2);

        assertThat(valued.get(30).positionRank()).isEqualTo("LW1");
        assertThat(valued.get(30).overReplacement()).isCloseTo(85 - 40, within(1e-9));
    }

    @Test
    void goaliesAreMeasuredAgainstGoaliesOnly() {
        player(1, 100, "C");
        player(2, 10, "C");
        player(41, 60, "G");
        player(42, 55, "G");
        player(43, 20, "G");

        Map<Integer, ReplacementLevel.Valued> valued = ReplacementLevel.value(values, positions, slots(1, 0, 0, 0, 1, 1), 2);

        assertThat(valued.get(41).overReplacement()).as("Util takes no goalie").isCloseTo(40, within(1e-9));
        assertThat(valued.get(41).positionRank()).isEqualTo("G1");
    }

    @Test
    void wherePositionEveryoneStartsTheWeakestOfThemIsTheLevel() {
        player(1, 100, "C");
        player(2, 70, "C");

        Map<Integer, ReplacementLevel.Valued> valued = ReplacementLevel.value(values, positions, slots(1, 0, 0, 0, 0, 0), 4);

        assertThat(valued.get(1).overReplacement()).isCloseTo(30, within(1e-9));
        assertThat(valued.get(2).overReplacement()).isZero();
    }

    @Test
    void aPlayerWithNoPositionIsLeftOut() {
        player(1, 100, "C");
        values.put(99, 500.0);

        assertThat(ReplacementLevel.value(values, positions, slots(1, 0, 0, 0, 0, 0), 2)).doesNotContainKey(99);
    }
}
