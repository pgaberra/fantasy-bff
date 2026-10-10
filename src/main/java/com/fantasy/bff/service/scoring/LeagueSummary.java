package com.fantasy.bff.service.scoring;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * What a drafted league adds up to: every team's totals, and the players behind each one.
 *
 * @param categoryKeys the stats the league counts, in the order the league gave them; the labels
 *     are the web's to write
 * @param positionKeys the lineup slots the league starts players in; the bench scores nothing
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
     * @param fullTotal what the team would score were every player it holds started in every game
     *     he plays, the ones it does not count included: the sum of its players' full values
     * @param fullValues the category cells on that same footing, which sum to the full total; no
     *     position cells, since a lineup slot is what starting decides
     * @param roster the team's players, best first by what their lineup starts them for
     * @param positionPlayers who starts in each lineup slot, and for how much
     */
    public record Team(
            String teamId,
            String name,
            boolean mine,
            double total,
            Map<String, Double> values,
            double fullTotal,
            Map<String, Double> fullValues,
            List<RosterRow> roster,
            Map<String, List<Contributor>> positionPlayers) {
    }

    /**
     * One of a team's players, as a row under an expanded team.
     *
     * @param team the NHL club he plays for, as the pool spells it; null where it lists none
     * @param positions the positions he is eligible at, G alone for a goalie
     * @param values his raw stat per category key; null where the stat is not of his kind
     * @param total what he counts for the team: his value over the games his lineup starts him in
     * @param fullValue his value over every game he plays, as if his lineup started him in all of
     *     them; what a player the team does not count would be worth if it did
     * @param contributions his share of the team's cell per category key, over those games; null
     *     likewise
     * @param fullContributions his share of the team's full cell per category key, over every game
     *     he plays; null likewise
     * @param reserveSlot the injured-reserve or not-active slot he is parked in today (IR, IR+,
     *     IR-LT, IR-NR or NA, in the platform's spelling), or null for a player in neither
     * @param counted whether he is among the players the team's lineup is picked from: its best,
     *     as many as its roster holds; one who is not counts for nothing
     */
    public record RosterRow(
            int playerId,
            String name,
            String team,
            List<String> positions,
            double total,
            double fullValue,
            Map<String, Double> values,
            Map<String, Double> contributions,
            Map<String, Double> fullContributions,
            String reserveSlot,
            boolean counted) {
    }

    /** A player under a lineup slot, and what he is worth there. */
    public record Contributor(String name, double value) {
    }
}
