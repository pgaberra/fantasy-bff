package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.PlannerMyTeamResponse;
import org.springframework.stereotype.Service;

/**
 * The streamer planner's own-team half: the user's team in the chosen league, so the client can
 * place its players in the league's lineup night by night and see which slots are left open.
 *
 * <p>Which nights a player plays is his club's schedule, which the client already has; this only
 * says who he is, where he may start and whether he is out. The roster is read live
 * ({@link LeagueOwnTeam}).
 */
@Service
public class StreamerPlannerMyTeamService {

    private final StreamerPlannerAvailability availability;
    private final LeagueOwnTeam ownTeam;

    public StreamerPlannerMyTeamService(StreamerPlannerAvailability availability, LeagueOwnTeam ownTeam) {
        this.availability = availability;
        this.ownTeam = ownTeam;
    }

    public PlannerMyTeamResponse myTeam(String userId, PlayerIdSpace platform, String leagueId) {
        availability.requireMyTeam();
        LeagueOwnTeam.Team team = ownTeam.read(userId, platform, leagueId);
        return new PlannerMyTeamResponse(
                team.found(),
                team.name(),
                team.players().stream().map(LeagueOwnTeam.Rostered::player).toList());
    }
}
