package com.fantasy.bff.service;

import com.fantasy.bff.generated.db.model.SharedPlayer;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Which rows of a published board a visitor who is not signed in gets to read, and in which
 * order.
 *
 * <p>The two are one decision. The page behind a share link lets a visitor sort by any column and
 * narrow the board by position, name, team and rookie status, and for a signed-in reader the
 * browser does that over the whole board. An anonymous reader holds the top rows only, so doing
 * it there answers "who scores the most goals" with whoever among the top 25 by rank scores most
 * — a different question, asked with the same control and giving no sign that it changed, and a
 * search box that finds nobody it was not already showing. Filtering and ordering the whole board
 * here and cutting afterwards makes the answer the one the control promises, and the rows behind
 * the gate still never leave the BFF.
 *
 * <p>The comparators mirror the ones the web sorts with, because a reader who signs in mid-visit
 * must not watch the same column reorder itself. Where they cannot mirror exactly — locale-aware
 * name ordering is the browser's {@code localeCompare} here and a {@link Collator} there — they
 * are as close as the two runtimes allow.
 */
public final class SharedBoardPreview {

    /** Sorting by the published ranking, which is what the share was ordered by. */
    public static final String RANK_COLUMN = "summary";

    private static final String NAME_COLUMN = "name";
    private static final String ASCENDING = "asc";

    private SharedBoardPreview() {
    }

    /**
     * The top {@code rows} of {@code board} under the visitor's filters and one column's order.
     *
     * <p>Nothing here rejects a value it does not know. This is a public page reached by a link,
     * and an unknown column is a client we have not deployed yet or a hand-edited URL — neither
     * is worth a 400. A column no row carries leaves every row comparing equal, so the board
     * comes back in the order it was published, which is the same answer the page opens with.
     * A filter nothing matches is left to answer with no rows, which is what the same filter
     * does in the browser.
     */
    public static List<SharedPlayer> select(List<SharedPlayer> board, SharedBoardFilters filters,
                                            String sort, String direction, int rows) {
        return board.stream()
                .filter(filters::matches)
                .sorted(comparator(sort, isAscending(direction)))
                .limit(rows)
                .toList();
    }

    private static boolean isAscending(String direction) {
        return ASCENDING.equalsIgnoreCase(direction);
    }

    private static Comparator<SharedPlayer> comparator(String sort, boolean ascending) {
        int sign = ascending ? 1 : -1;
        if (NAME_COLUMN.equals(sort)) {
            Collator collator = Collator.getInstance(Locale.ENGLISH);
            return (first, second) -> sign * collator.compare(first.getName(), second.getName());
        }
        if (sort == null || sort.isBlank() || RANK_COLUMN.equals(sort)) {
            // Reversed against the others because rank counts the good way down: descending — the
            // order the page opens in — is rank 1 at the top.
            return (first, second) -> sign * Integer.compare(second.getRank(), first.getRank());
        }
        return (first, second) -> compareStats(statValue(first, sort), statValue(second, sort), sign);
    }

    /**
     * Null rather than zero where the stat does not apply: a skater has no goals against, which is
     * not the same as conceding none. Scoring wins a key clash, as it does in the browser.
     */
    private static Double statValue(SharedPlayer player, String key) {
        Double scored = player.getStats().getScoring().get(key);
        return scored != null ? scored : player.getStats().getUtility().get(key);
    }

    /** Sinks the players a stat does not apply to, whichever way the column is pointing. */
    private static int compareStats(Double first, Double second, int sign) {
        if (first == null || second == null) {
            if (first == null && second == null) {
                return 0;
            }
            return first == null ? 1 : -1;
        }
        return sign * Double.compare(first, second);
    }
}
