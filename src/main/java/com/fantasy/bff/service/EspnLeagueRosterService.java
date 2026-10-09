package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.LeagueDraftStatus;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import com.fantasy.bff.generated.espn.model.LeagueRosterTeam;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Who each team in an ESPN league holds today, in the pool's numbering: the ESPN half of what
 * {@link YahooLeagueRosterService} and {@link YahooLeagueDraftService} give a Yahoo league's
 * power rankings.
 *
 * <p>A drafted player is on his ESPN roster at once, so the rosters are also a draft's picks so
 * far, and no read of the draft is needed — which is as well, since ESPN's league API lists a
 * draft's picks only once the draft is over.
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
     * @param teams the league's teams, ids as {@link #teamId} writes them
     * @param players each team's players by team id; a player the pool has no counterpart for is
     *     kept under the negative of his ESPN id (no pool id is negative, so he cannot be taken for
     *     somebody else), so he counts as unprojected rather than vanishing
     * @param reserve the players, on any team and numbered as in {@code players}, parked today in an
     *     injured-reserve slot, which holds a player without taking one of the roster's spots
     */
    public record Rosters(
            LeagueDraftStatus status,
            List<LeagueDraftTeam> teams,
            Map<String, List<Integer>> players,
            Set<Integer> reserve) {
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
        Set<Integer> reserve = new HashSet<>();
        for (LeagueRosterTeam team : response.getTeams()) {
            String teamId = teamId(response.getLeagueId(), team.getTeamId());
            teams.add(new LeagueDraftTeam(teamId, team.getName(), Boolean.TRUE.equals(team.getMine())));
            players.put(teamId, team.getPlayerIds().stream()
                    .map(espnId -> poolId(poolIds, espnId))
                    .toList());
            team.getPlayers().stream()
                    .filter(player -> LeagueOwnTeam.parked(player.getLineupSlot()))
                    .forEach(player -> reserve.add(poolId(poolIds, player.getEspnId())));
        }
        return new Rosters(status(response.getStatus()), teams, players, reserve);
    }

    private static int poolId(Map<Long, Integer> poolIds, long espnId) {
        return poolIds.getOrDefault(espnId, -Math.toIntExact(espnId));
    }

    /**
     * ESPN numbers a team only within its league, so the league goes into the id the rankings key a
     * team by: a bare "3" could be any league's third team.
     */
    static String teamId(String leagueId, int espnTeamId) {
        return "espn.l." + leagueId + ".t." + espnTeamId;
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
