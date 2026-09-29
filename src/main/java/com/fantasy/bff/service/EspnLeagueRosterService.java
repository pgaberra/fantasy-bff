package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.LeagueDraftStatus;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import com.fantasy.bff.generated.espn.model.LeagueRosterTeam;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Who each team in an ESPN league holds today, in the pool's numbering: the ESPN half of what
 * {@link YahooLeagueRosterService} and {@link YahooLeagueDraftService} give a Yahoo league's
 * power rankings.
 *
 * <p>A drafted player is on his ESPN roster at once, so the rosters are also a draft's picks so
 * far, and no second read of the draft is needed — which also keeps the rankings free of the
 * switch that lets a draft room follow an ESPN draft.
 */
@Service
public class EspnLeagueRosterService {

    private final EspnServiceClient espnServiceClient;
    private final EspnPoolIdCrosswalk crosswalk;

    public EspnLeagueRosterService(EspnServiceClient espnServiceClient, EspnPoolIdCrosswalk crosswalk) {
        this.espnServiceClient = espnServiceClient;
        this.crosswalk = crosswalk;
    }

    /**
     * @param status where the league's draft has got to
     * @param teams the league's teams, ids as {@link EspnLeagueDraftService#teamId} writes them
     * @param players each team's players by team id; a player the pool has no counterpart for is
     *     kept under the negative of his ESPN id, as a draft's pick is, so he counts as unprojected
     *     rather than vanishing
     */
    public record Rosters(LeagueDraftStatus status, List<LeagueDraftTeam> teams, Map<String, List<Integer>> players) {
    }

    public Rosters rosters(String appUserId, String leagueId) {
        var response = espnServiceClient.rosters(appUserId, leagueId);
        List<Long> espnIds = response.getTeams().stream()
                .flatMap(team -> team.getPlayerIds().stream())
                .toList();
        // A league yet to draft asks for no crosswalk, which is two whole pools matched.
        Map<Long, Integer> poolIds = espnIds.isEmpty() ? Map.of() : crosswalk.poolIds(espnIds);

        List<LeagueDraftTeam> teams = new ArrayList<>();
        Map<String, List<Integer>> players = new LinkedHashMap<>();
        for (LeagueRosterTeam team : response.getTeams()) {
            String teamId = EspnLeagueDraftService.teamId(response.getLeagueId(), team.getTeamId());
            teams.add(new LeagueDraftTeam(teamId, team.getName(), Boolean.TRUE.equals(team.getMine())));
            players.put(teamId, team.getPlayerIds().stream()
                    .map(espnId -> poolIds.getOrDefault(espnId, -Math.toIntExact(espnId)))
                    .toList());
        }
        return new Rosters(status(response.getStatus()), teams, players);
    }

    private static LeagueDraftStatus status(
            com.fantasy.bff.generated.espn.model.LeagueRostersResponse.StatusEnum status) {
        if (status == null) {
            return LeagueDraftStatus.UNKNOWN;
        }
        return switch (status) {
            case PRE_DRAFT -> LeagueDraftStatus.PRE_DRAFT;
            case IN_PROGRESS -> LeagueDraftStatus.IN_PROGRESS;
            case FINISHED -> LeagueDraftStatus.FINISHED;
            default -> LeagueDraftStatus.UNKNOWN;
        };
    }
}
