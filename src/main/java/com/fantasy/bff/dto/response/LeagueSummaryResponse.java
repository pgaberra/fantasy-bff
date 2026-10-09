package com.fantasy.bff.dto.response;

import com.fantasy.bff.service.scoring.LeagueSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Where a league stands: the teams, best first, with each one's totals and the players behind
 * them — its roster rows and who fills each lineup slot — for every signed-in manager.
 */
@Schema(description = "A league's teams, totalled against a projection")
public record LeagueSummaryResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The projection the players were scored against")
        SummarySource source,

        @Schema(description = "The model version behind the numbers; absent for last season's stats")
        String modelVersion,

        @Schema(description = "The board the players were scored against; present only for "
                + "`source: projection`")
        String projectionId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, deprecated = true,
                description = "Always true: the per-player halves are filled in for everyone. "
                        + "Kept for web builds that still read it.")
        boolean premium,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How the league scores, which decides what the totals are: fantasy "
                        + "points, or a z-score across the categories it counts")
        ScoringBasis scoringType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Where the league's draft has got to")
        LeagueDraftStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How many picks the league has made; zero before it drafts")
        int picks,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How many of the players the teams hold have no line in the projection "
                        + "and so count for nothing — a board that leaves players out, most often "
                        + "a spreadsheet import")
        int unprojectedPlayers,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the model's lines are its in-season ones: each player's "
                        + "rest of the season from the last nightly run, on the season line's "
                        + "scale, which is what the model is ranked by once the season is under "
                        + "way. False for a board and last season's stats, which are whole seasons "
                        + "as they stand, and before the first game.")
        boolean inSeason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The stats the league counts, in its own order. The labels are the "
                        + "client's to write.")
        List<String> categoryKeys,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The lineup slots the league starts players in. The bench scores "
                        + "nothing, so it has no column")
        List<String> positionKeys,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Teams, best total first")
        List<Team> teams
) {

    /** One team's line in the table. */
    @Schema(name = "LeagueSummaryTeam")
    public record Team(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String teamId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Whether this is the signed-in manager's team")
            boolean mine,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "What the team's lineup will score: night by night over the "
                            + "schedule, the players with a game fill the league's slots best "
                            + "first and the rest are benched, so each player counts for the "
                            + "share of his games it starts him in")
            double total,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The cell per category key and per lineup slot. Each set sums to "
                            + "the total.")
            Map<String, Double> values,
            @Schema(description = "The team's players, best first by what they count for: its "
                    + "current roster once the draft is over, its picks until then.")
            List<RosterRow> roster,
            @Schema(description = "Who fills each lineup slot.")
            Map<String, List<Contributor>> positionPlayers) {
    }

    /** One of a team's players, as a row under an expanded team. */
    @Schema(name = "LeagueSummaryRosterRow")
    public record RosterRow(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int playerId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(description = "The NHL club he plays for, as the player pool spells it; absent "
                    + "where the pool lists none")
            String team,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The positions he is eligible at, in lineup order; G alone for a "
                            + "goalie")
            List<String> positions,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "What he counts for the team: his value over the games its "
                            + "lineup starts him in")
            double total,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "His value over every game he plays, as if his lineup started him in "
                            + "all of them: what a player the team does not count would be worth if it did")
            double fullValue,
            @Schema(description = "His raw stat per category key; null where the stat is not his kind")
            Map<String, Double> values,
            @Schema(description = "His share of the team's cell per category key, over the games he "
                    + "starts; null likewise")
            Map<String, Double> contributions,
            @Schema(description = "The injured-reserve or not-active slot he is parked in today (IR, "
                    + "IR+, IR-LT, IR-NR or NA), which holds a player without taking a roster spot; "
                    + "absent for a player in neither")
            String reserveSlot,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Whether he is among the players the team's lineup is picked from: "
                            + "its best, as many as the roster holds. One who is not counts for nothing")
            boolean counted) {
    }

    /** A player under a lineup slot, and what he is worth there. */
    @Schema(name = "LeagueSummaryContributor")
    public record Contributor(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "His value over the games he starts in this slot")
            double value) {
    }

    public static LeagueSummaryResponse from(
            LeagueSummary summary,
            SummarySource source,
            String modelVersion,
            String projectionId,
            ScoringBasis scoringType,
            LeagueDraftStatus status,
            int picks,
            int unprojectedPlayers,
            boolean inSeason) {
        return new LeagueSummaryResponse(
                source,
                modelVersion,
                projectionId,
                true,
                scoringType,
                status,
                picks,
                unprojectedPlayers,
                inSeason,
                summary.categoryKeys(),
                summary.positionKeys(),
                summary.teams().stream().map(LeagueSummaryResponse::team).toList());
    }

    private static Team team(LeagueSummary.Team team) {
        return new Team(
                team.teamId(),
                team.name(),
                team.mine(),
                team.total(),
                team.values(),
                team.roster() == null
                        ? null
                        : team.roster().stream().map(LeagueSummaryResponse::rosterRow).toList(),
                team.positionPlayers() == null ? null : contributors(team.positionPlayers()));
    }

    private static RosterRow rosterRow(LeagueSummary.RosterRow row) {
        return new RosterRow(
                row.playerId(), row.name(), row.team(), row.positions(), row.total(), row.fullValue(), row.values(),
                row.contributions(), row.reserveSlot(), row.counted());
    }

    private static Map<String, List<Contributor>> contributors(
            Map<String, List<LeagueSummary.Contributor>> players) {
        Map<String, List<Contributor>> mapped = new java.util.LinkedHashMap<>();
        players.forEach((slot, list) -> mapped.put(
                slot,
                list.stream()
                        .map(player -> new Contributor(player.name(), player.value()))
                        .toList()));
        return mapped;
    }
}
