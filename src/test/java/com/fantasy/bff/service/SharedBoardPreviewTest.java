package com.fantasy.bff.service;

import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.SharedPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rows an anonymous visitor reads. What these are really checking is that the answer to a
 * column comes from the whole board: the fixture puts the interesting players deliberately far
 * down the published ranking, where a preview cut before sorting would never reach them.
 */
class SharedBoardPreviewTest {

    private static SharedPlayer skater(int rank, String name, List<String> positions, double goals) {
        return player(rank, name, SharedPlayer.TypeEnum.SKATER, positions, Map.of("goals", goals));
    }

    private static SharedPlayer goalie(int rank, String name, double wins) {
        return player(rank, name, SharedPlayer.TypeEnum.GOALIE, null, Map.of("w", wins));
    }

    private static SharedPlayer player(int rank, String name, SharedPlayer.TypeEnum type,
                                       List<String> positions, Map<String, Double> scoring) {
        return new SharedPlayer()
                .playerId(rank)
                .name(name)
                .type(type)
                .positions(positions)
                .rank(rank)
                .value(500.0 - rank)
                .stats(new PlayerStats().utility(Map.of("gp", 82.0)).scoring(scoring));
    }

    /** Ranked 1..5; the best goal scorer sits fourth and the only defenceman fifth. */
    private static final List<SharedPlayer> BOARD = List.of(
            skater(1, "Connor McDavid", List.of("C"), 40.0),
            skater(2, "Nathan MacKinnon", List.of("C"), 30.0),
            goalie(3, "Igor Shesterkin", 36.0),
            skater(4, "Auston Matthews", List.of("C", "LW"), 60.0),
            skater(5, "Cale Makar", List.of("D"), 20.0));

    private static List<String> names(List<SharedPlayer> rows) {
        return rows.stream().map(SharedPlayer::getName).toList();
    }

    @Test
    @DisplayName("with no column asked for, the published ranking is what a visitor gets")
    void defaultsToThePublishedOrder() {
        assertThat(names(SharedBoardPreview.select(BOARD, null, null, null, 3)))
                .containsExactly("Connor McDavid", "Nathan MacKinnon", "Igor Shesterkin");
    }

    @Test
    @DisplayName("a stat column answers with the board's best, not the preview's")
    void sortsTheWholeBoardBeforeCutting() {
        assertThat(names(SharedBoardPreview.select(BOARD, null, "goals", "desc", 2)))
                .containsExactly("Auston Matthews", "Connor McDavid");
    }

    @Test
    void sortsTheOtherWayWhenAsked() {
        assertThat(names(SharedBoardPreview.select(BOARD, null, "goals", "asc", 2)))
                .containsExactly("Cale Makar", "Nathan MacKinnon");
    }

    @Test
    @DisplayName("a stat a player cannot have sinks them, whichever way the column points")
    void sinksPlayersTheStatDoesNotApplyTo() {
        assertThat(names(SharedBoardPreview.select(BOARD, null, "goals", "asc", 5)))
                .last().isEqualTo("Igor Shesterkin");
        assertThat(names(SharedBoardPreview.select(BOARD, null, "goals", "desc", 5)))
                .last().isEqualTo("Igor Shesterkin");
    }

    @Test
    void filtersTheWholeBoardByPosition() {
        assertThat(names(SharedBoardPreview.select(BOARD, "D", null, null, 25)))
                .containsExactly("Cale Makar");
        assertThat(names(SharedBoardPreview.select(BOARD, "G", null, null, 25)))
                .containsExactly("Igor Shesterkin");
        assertThat(names(SharedBoardPreview.select(BOARD, "SKATER", null, null, 25)))
                .doesNotContain("Igor Shesterkin");
    }

    @Test
    @DisplayName("a skater filters under every position they are eligible at")
    void matchesAnyOfASkatersPositions() {
        assertThat(names(SharedBoardPreview.select(BOARD, "LW", null, null, 25)))
                .containsExactly("Auston Matthews");
    }

    @Test
    void filterAndColumnApplyTogether() {
        assertThat(names(SharedBoardPreview.select(BOARD, "C", "goals", "desc", 2)))
                .containsExactly("Auston Matthews", "Connor McDavid");
    }

    @Test
    void sortsByNameWhenAsked() {
        assertThat(names(SharedBoardPreview.select(BOARD, null, "name", "asc", 2)))
                .containsExactly("Auston Matthews", "Cale Makar");
    }

    @Test
    @DisplayName("the published ranking reverses like any other column")
    void ranksTheOtherWayRound() {
        assertThat(names(SharedBoardPreview.select(BOARD, null, "summary", "asc", 2)))
                .containsExactly("Cale Makar", "Auston Matthews");
    }

    @Test
    @DisplayName("a column no row carries leaves the board as it was published")
    void ignoresAnUnknownColumn() {
        assertThat(names(SharedBoardPreview.select(BOARD, null, "not-a-stat", "desc", 3)))
                .containsExactly("Connor McDavid", "Nathan MacKinnon", "Igor Shesterkin");
    }

    @Test
    void asksForNoMoreRowsThanTheBoardHas() {
        assertThat(SharedBoardPreview.select(BOARD, null, null, null, 25)).hasSize(5);
    }
}
