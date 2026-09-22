package com.fantasy.bff.service.scoring;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * What a drafted league adds up to: every team's totals, and — for whoever may see them — the
 * players behind each one.
 *
 * <p>The aggregates are the whole of what a free account is shown, which is why they are computed
 * here rather than in the browser: a total the browser worked out is a total the browser was given
 * the lines for. The per-player halves ({@code roster}, {@code positionPlayers}) are dropped
 * before the response leaves for an account without premium.
 *
 * @param categoryKeys the stats the league counts, in the order the league gave them; the labels
 *     are the web's to write
 * @param positionKeys the lineup slots that hold anyone, bench last
 * @param teams the teams, best total first
 */
@Schema(description = "A drafted league's teams, totalled by category and by lineup slot")
public record LeagueSummary(
        List<String> categoryKeys,
        List<String> positionKeys,
        List<Team> teams) {

    /**
     * One team's line in the table.
     *
     * @param values the cell per category key and per position key; the category cells sum to the
     *     total, and so do the position cells
     * @param roster the drafted players, best first — premium only, null otherwise
     * @param positionPlayers who fills each lineup slot — premium only, null otherwise
     */
    public record Team(
            String teamId,
            String name,
            boolean mine,
            double total,
            Map<String, Double> values,
            List<RosterRow> roster,
            Map<String, List<Contributor>> positionPlayers) {

        /** The same line with everything per-player taken out of it. */
        public Team aggregatesOnly() {
            return new Team(teamId, name, mine, total, values, null, null);
        }
    }

    /**
     * One drafted player, as a row under an expanded team.
     *
     * @param values his raw stat per category key; null where the stat is not of his kind
     * @param contributions his share of the team's cell per category key; null likewise
     */
    public record RosterRow(
            int playerId,
            String name,
            double total,
            Map<String, Double> values,
            Map<String, Double> contributions) {
    }

    /** A player under a lineup slot, and what he is worth there. */
    public record Contributor(String name, double value) {
    }

    /** The same summary with every per-player half dropped. */
    public LeagueSummary aggregatesOnly() {
        return new LeagueSummary(
                categoryKeys, positionKeys, teams.stream().map(Team::aggregatesOnly).toList());
    }
}
