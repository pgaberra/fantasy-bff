package com.fantasy.bff.service;

import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.SharedPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rows an anonymous visitor reads. What these are really checking is that the answer to a
 * column or a control comes from the whole board: the fixture puts the interesting players
 * deliberately far down the published ranking, where a preview cut before filtering and sorting
 * would never reach them.
 */
class SharedBoardPreviewTest {

    private static SharedPlayer skater(int rank, String name, String team, List<String> positions,
                                       double goals) {
        return player(rank, name, team, SharedPlayer.TypeEnum.SKATER, positions,
                Map.of("goals", goals));
    }

    private static SharedPlayer goalie(int rank, String name, String team, double wins) {
        return player(rank, name, team, SharedPlayer.TypeEnum.GOALIE, null, Map.of("w", wins));
    }

    private static SharedPlayer player(int rank, String name, String team, SharedPlayer.TypeEnum type,
                                       List<String> positions, Map<String, Double> scoring) {
        return new SharedPlayer()
                .playerId(rank)
                .name(name)
                .teamAbbrev(team)
                .type(type)
                .positions(positions)
                .rank(rank)
                .value(500.0 - rank)
                .stats(new PlayerStats().utility(Map.of("gp", 82.0)).scoring(scoring));
    }

    /** Ranked 1..5; the best goal scorer sits fourth and the only defenceman fifth. */
    private static final List<SharedPlayer> BOARD = List.of(
            skater(1, "Connor McDavid", "EDM", List.of("C"), 40.0),
            skater(2, "Nathan MacKinnon", "COL", List.of("C"), 30.0),
            goalie(3, "Igor Shesterkin", "NYR", 36.0),
            skater(4, "Auston Matthews", "TOR", List.of("C", "LW"), 60.0),
            skater(5, "Cale Makar", "COL", List.of("D"), 20.0));

    private static List<String> names(List<SharedPlayer> rows) {
        return rows.stream().map(SharedPlayer::getName).toList();
    }

    private static SharedBoardFilters at(String position) {
        return new SharedBoardFilters(position, null, null, null);
    }

    private static SharedBoardFilters searchingFor(String search) {
        return new SharedBoardFilters(null, search, null, null);
    }

    private static SharedBoardFilters onTeam(String team) {
        return new SharedBoardFilters(null, null, team, null);
    }

    private static SharedBoardFilters rookies(Integer... playerIds) {
        return new SharedBoardFilters(null, null, null, Set.of(playerIds));
    }

    @Test
    @DisplayName("with no column asked for, the published ranking is what a visitor gets")
    void defaultsToThePublishedOrder() {
        assertThat(names(SharedBoardPreview.select(BOARD, SharedBoardFilters.NONE, null, null, 3)))
                .containsExactly("Connor McDavid", "Nathan MacKinnon", "Igor Shesterkin");
    }

    @Test
    @DisplayName("a stat column answers with the board's best, not the preview's")
    void sortsTheWholeBoardBeforeCutting() {
        assertThat(names(SharedBoardPreview.select(BOARD, SharedBoardFilters.NONE, "goals", "desc", 2)))
                .containsExactly("Auston Matthews", "Connor McDavid");
    }

    @Test
    void sortsTheOtherWayWhenAsked() {
        assertThat(names(SharedBoardPreview.select(BOARD, SharedBoardFilters.NONE, "goals", "asc", 2)))
                .containsExactly("Cale Makar", "Nathan MacKinnon");
    }

    @Test
    @DisplayName("a stat a player cannot have sinks them, whichever way the column points")
    void sinksPlayersTheStatDoesNotApplyTo() {
        assertThat(names(SharedBoardPreview.select(BOARD, SharedBoardFilters.NONE, "goals", "asc", 5)))
                .last().isEqualTo("Igor Shesterkin");
        assertThat(names(SharedBoardPreview.select(BOARD, SharedBoardFilters.NONE, "goals", "desc", 5)))
                .last().isEqualTo("Igor Shesterkin");
    }

    @Test
    void filtersTheWholeBoardByPosition() {
        assertThat(names(SharedBoardPreview.select(BOARD, at("D"), null, null, 25)))
                .containsExactly("Cale Makar");
        assertThat(names(SharedBoardPreview.select(BOARD, at("G"), null, null, 25)))
                .containsExactly("Igor Shesterkin");
        assertThat(names(SharedBoardPreview.select(BOARD, at("SKATER"), null, null, 25)))
                .doesNotContain("Igor Shesterkin");
    }

    @Test
    @DisplayName("a skater filters under every position they are eligible at")
    void matchesAnyOfASkatersPositions() {
        assertThat(names(SharedBoardPreview.select(BOARD, at("LW"), null, null, 25)))
                .containsExactly("Auston Matthews");
    }

    @Test
    @DisplayName("a search reaches players the preview would never have carried")
    void searchesTheWholeBoard() {
        assertThat(names(SharedBoardPreview.select(BOARD, searchingFor("makar"), null, null, 2)))
                .containsExactly("Cale Makar");
    }

    @Test
    @DisplayName("a search matches part of a name, in either case")
    void searchesBySubstringWithoutCase() {
        assertThat(names(SharedBoardPreview.select(BOARD, searchingFor("MCDA"), null, null, 25)))
                .containsExactly("Connor McDavid");
        assertThat(names(SharedBoardPreview.select(BOARD, searchingFor("  con  "), null, null, 25)))
                .containsExactly("Connor McDavid");
    }

    @Test
    @DisplayName("a search nobody matches answers with nothing, as it does in the browser")
    void searchesForSomeoneWhoIsNotOnTheBoard() {
        assertThat(SharedBoardPreview.select(BOARD, searchingFor("gretzky"), null, null, 25))
                .isEmpty();
    }

    @Test
    void filtersTheWholeBoardByTeam() {
        assertThat(names(SharedBoardPreview.select(BOARD, onTeam("COL"), null, null, 25)))
                .containsExactly("Nathan MacKinnon", "Cale Makar");
        assertThat(names(SharedBoardPreview.select(BOARD, onTeam("ALL"), null, null, 25)))
                .hasSize(5);
    }

    @Test
    @DisplayName("rookies are kept by id, since the snapshot does not say who is one")
    void keepsOnlyTheRookiesWhenAsked() {
        assertThat(names(SharedBoardPreview.select(BOARD, rookies(4, 5), null, null, 25)))
                .containsExactly("Auston Matthews", "Cale Makar");
    }

    @Test
    void filtersApplyTogether() {
        SharedBoardFilters both = new SharedBoardFilters("C", "a", "COL", null);
        assertThat(names(SharedBoardPreview.select(BOARD, both, null, null, 25)))
                .containsExactly("Nathan MacKinnon");
    }

    @Test
    void filterAndColumnApplyTogether() {
        assertThat(names(SharedBoardPreview.select(BOARD, at("C"), "goals", "desc", 2)))
                .containsExactly("Auston Matthews", "Connor McDavid");
    }

    @Test
    void sortsByNameWhenAsked() {
        assertThat(names(SharedBoardPreview.select(BOARD, SharedBoardFilters.NONE, "name", "asc", 2)))
                .containsExactly("Auston Matthews", "Cale Makar");
    }

    @Test
    @DisplayName("the published ranking reverses like any other column")
    void ranksTheOtherWayRound() {
        assertThat(names(SharedBoardPreview.select(BOARD, SharedBoardFilters.NONE, "summary", "asc", 2)))
                .containsExactly("Cale Makar", "Auston Matthews");
    }

    @Test
    @DisplayName("a column no row carries leaves the board as it was published")
    void ignoresAnUnknownColumn() {
        assertThat(names(SharedBoardPreview.select(BOARD, SharedBoardFilters.NONE, "not-a-stat", "desc", 3)))
                .containsExactly("Connor McDavid", "Nathan MacKinnon", "Igor Shesterkin");
    }

    @Test
    void asksForNoMoreRowsThanTheBoardHas() {
        assertThat(SharedBoardPreview.select(BOARD, SharedBoardFilters.NONE, null, null, 25)).hasSize(5);
    }
}
