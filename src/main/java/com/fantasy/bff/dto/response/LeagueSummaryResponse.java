package com.fantasy.bff.dto.response;

import com.fantasy.bff.service.scoring.LeagueSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * What a league's draft came to: the teams, best first, with each one's totals.
 *
 * <p>The per-player halves of a team — its roster rows and who fills each lineup slot — are
 * filled in only for an account with premium. That is not a decision the browser could carry
 * out: the totals are computed here precisely so the lines behind them need never be sent.
 */
@Schema(description = "A league's drafted teams, totalled against a projection")
public record LeagueSummaryResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The projection the picks were scored against")
        SummarySource source,

        @Schema(description = "The model version behind the numbers; absent for last season's stats")
        String modelVersion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the per-player halves are filled in, which premium pays for")
        boolean premium,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Where the league's draft has got to")
        LeagueDraftStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How many picks the league has made; zero before it drafts")
        int picks,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The stats the league counts, in its own order. The labels are the "
                        + "client's to write.")
        List<String> categoryKeys,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The lineup slots that hold anyone, bench last")
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
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double total,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The cell per category key and per lineup slot. Each set sums to "
                            + "the total.")
            Map<String, Double> values,
            @Schema(description = "The drafted players, best first. Premium only; absent otherwise.")
            List<RosterRow> roster,
            @Schema(description = "Who fills each lineup slot. Premium only; absent otherwise.")
            Map<String, List<Contributor>> positionPlayers) {
    }

    /** One drafted player, as a row under an expanded team. */
    @Schema(name = "LeagueSummaryRosterRow")
    public record RosterRow(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int playerId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double total,
            @Schema(description = "His raw stat per category key; null where the stat is not his kind")
            Map<String, Double> values,
            @Schema(description = "His share of the team's cell per category key; null likewise")
            Map<String, Double> contributions) {
    }

    /** A player under a lineup slot, and what he is worth there. */
    @Schema(name = "LeagueSummaryContributor")
    public record Contributor(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double value) {
    }

    public static LeagueSummaryResponse from(
            LeagueSummary summary,
            SummarySource source,
            String modelVersion,
            boolean premium,
            LeagueDraftStatus status,
            int picks) {
        return new LeagueSummaryResponse(
                source,
                modelVersion,
                premium,
                status,
                picks,
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
        return new RosterRow(row.playerId(), row.name(), row.total(), row.values(), row.contributions());
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
