package com.fantasy.bff.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which projection a league summary request names, given the board it may name beside it. */
class SummarySourceTest {

    private static final UUID BOARD = UUID.fromString("00000000-0000-0000-0000-0000000000b0");

    @Test
    @DisplayName("names nothing: the model")
    void defaultsToTheModel() {
        assertThat(SummarySource.from(null, null)).isEqualTo(SummarySource.MODEL);
        assertThat(SummarySource.from(" ", null)).isEqualTo(SummarySource.MODEL);
    }

    @Test
    @DisplayName("a board alone implies the board as the source")
    void boardImpliesProjection() {
        assertThat(SummarySource.from(null, BOARD)).isEqualTo(SummarySource.PROJECTION);
        assertThat(SummarySource.from("projection", BOARD)).isEqualTo(SummarySource.PROJECTION);
    }

    @Test
    @DisplayName("last season is still last season")
    void lastSeason() {
        assertThat(SummarySource.from("last_season", null)).isEqualTo(SummarySource.LAST_SEASON);
    }

    @Test
    @DisplayName("a board beside another source is refused")
    void boardBesideAnotherSourceIsRefused() {
        assertThatThrownBy(() -> SummarySource.from("model", BOARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("source=projection without a board is refused")
    void projectionWithoutABoardIsRefused() {
        assertThatThrownBy(() -> SummarySource.from("projection", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
