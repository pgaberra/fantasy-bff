package com.fantasy.bff.service;

import com.fantasy.bff.generated.db.model.SharedPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How a visitor has narrowed a published board: the same four controls the editor's table offers,
 * as the public read takes them.
 *
 * <p>They matter here rather than only in the browser because they decide <em>which</em> rows an
 * anonymous visitor receives. A search box over the top 25 rows finds a player only if they were
 * already on screen, which is not what a search box promises — see {@link SharedBoardPreview}.
 *
 * <p>The rookie filter carries the ids rather than a flag: rookie status is not in the published
 * snapshot, so it is resolved once per request and handed in. {@code null} means the filter is
 * off, or that rookie status could not be determined — both leave the board alone, which is the
 * same thing the editor does when nobody can say who is a rookie.
 */
public record SharedBoardFilters(String position, String search, String team, Set<Integer> rookieIds) {

    /** Every row, in the order it was published: what a visitor gets before touching a control. */
    public static final SharedBoardFilters NONE = new SharedBoardFilters(null, null, null, null);

    private static final String ALL_POSITIONS = "ALL";
    private static final String SKATERS = "SKATER";
    private static final String GOALIES = "G";

    /** The value the team control sends for "every team", which is not a team abbreviation. */
    private static final String ALL_TEAMS = "ALL";

    /** Whether a row survives all four. */
    public boolean matches(SharedPlayer player) {
        return matchesPosition(player) && matchesSearch(player) && matchesTeam(player)
                && matchesRookie(player);
    }

    private boolean matchesPosition(SharedPlayer player) {
        if (isBlank(position) || ALL_POSITIONS.equalsIgnoreCase(position)) {
            return true;
        }
        boolean goalie = player.getType() == SharedPlayer.TypeEnum.GOALIE;
        if (GOALIES.equals(position)) {
            return goalie;
        }
        if (SKATERS.equalsIgnoreCase(position)) {
            return !goalie;
        }
        List<String> positions = player.getPositions();
        return positions != null && positions.contains(position);
    }

    /**
     * Matched the way the browser matches it: a case-insensitive substring of the name, so typing
     * "mcda" finds McDavid and half a surname finds whoever it belongs to.
     */
    private boolean matchesSearch(SharedPlayer player) {
        if (isBlank(search)) {
            return true;
        }
        return player.getName().toLowerCase(Locale.ROOT)
                .contains(search.trim().toLowerCase(Locale.ROOT));
    }

    private boolean matchesTeam(SharedPlayer player) {
        if (isBlank(team) || ALL_TEAMS.equalsIgnoreCase(team)) {
            return true;
        }
        return team.equals(player.getTeamAbbrev());
    }

    private boolean matchesRookie(SharedPlayer player) {
        return rookieIds == null || rookieIds.contains(player.getPlayerId());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
