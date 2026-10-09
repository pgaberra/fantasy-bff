package com.fantasy.bff.service;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.PlannerMyTeamResponse;
import com.fantasy.bff.dto.response.PlannerOwnLine;
import com.fantasy.bff.dto.response.PlannerRosterPlayer;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.generated.projection.model.RangeGoalieResponse;
import com.fantasy.bff.generated.projection.model.RangeProjectionsResponse;
import com.fantasy.bff.generated.projection.model.RangeSkaterResponse;
import com.fantasy.bff.service.mapping.PlayerFieldMapping;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The streamer planner's own-team half: the user's team in the chosen league, so the client can
 * place its players in the league's lineup night by night and see which slots are left open.
 *
 * <p>Which nights a player plays is his club's schedule, which the client already has; this only
 * says who he is, where he may start and whether he is out. The roster is read live
 * ({@link LeagueOwnTeam}).
 *
 * <p>Given a stretch, each player also goes out with the model's line over it, the line a free
 * agent carries ({@link StreamerPlannerFreeAgentService}), so the client can price a swap: what a
 * pickup's games are worth against the games of the player he would replace.
 */
@Service
public class StreamerPlannerMyTeamService {

    /** Projections asked of the model: the whole projected league, as the free agents ask. */
    private static final int PROJECTION_LIMIT = 2000;

    private final LeagueOwnTeam ownTeam;
    private final ProjectionServiceClient projectionServiceClient;
    private final NhlIdentityJoin identityJoin;

    public StreamerPlannerMyTeamService(
            LeagueOwnTeam ownTeam,
            ProjectionServiceClient projectionServiceClient,
            NhlIdentityJoin identityJoin) {
        this.ownTeam = ownTeam;
        this.projectionServiceClient = projectionServiceClient;
        this.identityJoin = identityJoin;
    }

    /**
     * @param start the stretch's first date; null, with {@code end}, for the team alone
     * @param end its last date, inclusive
     */
    public PlannerMyTeamResponse myTeam(
            String userId, PlayerIdSpace platform, String leagueId, LocalDate start, LocalDate end) {
        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException("start and end go together");
        }
        if (start != null) {
            StreamerPlannerService.requireStretch(start, end);
        }
        LeagueOwnTeam.Team team = ownTeam.read(userId, platform, leagueId);
        List<PlannerRosterPlayer> players =
                team.players().stream().map(LeagueOwnTeam.Rostered::player).toList();
        List<PlannerOwnLine> lines = start == null || players.isEmpty()
                ? List.of()
                : lines(team.players(), projectionServiceClient.rangeProjections(start, end, PROJECTION_LIMIT));
        return new PlannerMyTeamResponse(team.found(), team.name(), players, lines);
    }

    /** Each player's line over the stretch, joined to the model on identity as the wire is. */
    private List<PlannerOwnLine> lines(
            List<LeagueOwnTeam.Rostered> team, RangeProjectionsResponse projections) {
        if (projections == null) {
            return List.of();
        }
        Map<String, Long> nhlIds = new HashMap<>();
        identityJoin.join(team.stream()
                        .map(rostered -> new NhlIdentityJoin.PlatformPlayer(
                                rostered.player().playerId(),
                                rostered.player().name(),
                                rostered.player().teamAbbrev(),
                                rostered.sweaterNumber()))
                        .toList())
                .nhlIdToPlatformId()
                .forEach((nhlId, platformId) -> nhlIds.put(String.valueOf(platformId), nhlId));
        Map<Long, RangeSkaterResponse> skaters = new HashMap<>();
        for (RangeSkaterResponse line : projections.getSkaters()) {
            skaters.put(line.getNhlId().longValue(), line);
        }
        Map<Long, RangeGoalieResponse> goalies = new HashMap<>();
        for (RangeGoalieResponse line : projections.getGoalies()) {
            goalies.put(line.getNhlId().longValue(), line);
        }

        List<PlannerOwnLine> lines = new ArrayList<>();
        for (LeagueOwnTeam.Rostered rostered : team) {
            PlannerRosterPlayer player = rostered.player();
            Long nhlId = nhlIds.get(player.playerId());
            if (nhlId == null) {
                continue;
            }
            if ("goalie".equals(player.type())) {
                RangeGoalieResponse line = goalies.get(nhlId);
                if (line != null) {
                    lines.add(new PlannerOwnLine(
                            player.playerId(),
                            clubGames(line.getClubGames()),
                            StreamerPlannerFreeAgentService.number(line.getExpectedGames()),
                            StreamerPlannerFreeAgentService.goalieStats(line)));
                }
            } else {
                RangeSkaterResponse line = skaters.get(nhlId);
                if (line != null) {
                    lines.add(new PlannerOwnLine(
                            player.playerId(),
                            clubGames(line.getClubGames()),
                            StreamerPlannerFreeAgentService.number(line.getExpectedGames()),
                            StreamerPlannerFreeAgentService.skaterStats(line, playsDefence(player.positions()))));
                }
            }
        }
        return List.copyOf(lines);
    }

    private static boolean playsDefence(List<String> positions) {
        return positions.stream()
                .anyMatch(position -> PlayerFieldMapping.fantasyPosition(position) == SkaterPosition.D);
    }

    private static int clubGames(Integer clubGames) {
        return clubGames == null ? 0 : clubGames;
    }
}
