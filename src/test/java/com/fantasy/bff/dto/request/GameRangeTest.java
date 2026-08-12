package com.fantasy.bff.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GameRangeTest {

    @Test
    @DisplayName("a range that says two different things is rejected rather than resolved")
    void rejectsLastGamesAlongsideExplicitBounds() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GameRange(50, null, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> new GameRange(null, 82, 20));
    }

    @Test
    @DisplayName("a range that runs backwards is rejected")
    void rejectsInvertedBounds() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GameRange(60, 20, null));
    }

    @Test
    @DisplayName("a single-game range is a range")
    void allowsASingleGame() {
        assertThat(new GameRange(41, 41, null).fromGame()).isEqualTo(41);
    }

    @Test
    @DisplayName("either bound may stand alone — the other end is the season's")
    void allowsAHalfOpenRange() {
        assertThat(new GameRange(50, null, null).toGame()).isNull();
        assertThat(new GameRange(null, 41, null).fromGame()).isNull();
    }

    @Test
    @DisplayName("an entirely open range is the whole season")
    void seasonIsFullyOpen() {
        assertThat(GameRange.season()).isEqualTo(new GameRange(null, null, null));
    }

    @Test
    @DisplayName("the last-N shorthand carries no explicit bounds")
    void lastGamesShorthand() {
        assertThat(GameRange.ofLastGames(20)).isEqualTo(new GameRange(null, null, 20));
    }
}
