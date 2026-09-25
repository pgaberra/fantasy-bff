package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.LeagueDraftPick;
import com.fantasy.bff.dto.response.LeagueDraftResponse;
import com.fantasy.bff.dto.response.LeagueDraftStatus;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import com.fantasy.bff.generated.espn.model.LeagueDraftResponse.StatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** An ESPN league's draft, in the shape the draft room follows it by: the one a Yahoo league's has. */
@Service
public class EspnLeagueDraftService {

    private static final Logger log = LoggerFactory.getLogger(EspnLeagueDraftService.class);

    private final EspnServiceClient espnServiceClient;
    private final EspnPoolIdCrosswalk crosswalk;
    private final LeagueDraftSyncAvailability availability;

    public EspnLeagueDraftService(EspnServiceClient espnServiceClient, EspnPoolIdCrosswalk crosswalk,
                                  LeagueDraftSyncAvailability availability) {
        this.espnServiceClient = espnServiceClient;
        this.crosswalk = crosswalk;
        this.availability = availability;
    }

    public LeagueDraftResponse draft(String appUserId, String leagueId) {
        if (!availability.espnAvailable()) {
            throw new NoSuchElementException("Following an ESPN league's draft is not enabled");
        }
        var draft = espnServiceClient.draft(appUserId, leagueId);
        List<LeagueDraftTeam> teams = draft.getTeams().stream()
                .map(team -> new LeagueDraftTeam(
                        teamId(draft.getLeagueId(), team.getTeamId()), team.getName(),
                        Boolean.TRUE.equals(team.getMine())))
                .toList();
        return new LeagueDraftResponse(
                status(draft.getStatus()),
                Boolean.TRUE.equals(draft.getAuction()),
                teams,
                Boolean.TRUE.equals(draft.getOrderKnown()),
                madePicks(draft.getLeagueId(), draft.getPicks()));
    }

    /**
     * ESPN numbers a team only within its league, so the league goes into the id the board keeps:
     * a bare "3" could be any league's third team, or one of the board's own.
     */
    static String teamId(String leagueId, int espnTeamId) {
        return "espn.l." + leagueId + ".t." + espnTeamId;
    }

    /**
     * The unbroken run of picks from pick 1, since the board numbers a pick by its place in the
     * list. A player the pool has no counterpart for keeps his pick under the negative of his ESPN
     * id: dropping the pick would hand every later one to the wrong team, and no pool id is
     * negative, so it cannot be taken for somebody else.
     */
    private List<LeagueDraftPick> madePicks(
            String leagueId, List<com.fantasy.bff.generated.espn.model.LeagueDraftPick> picks) {
        var sorted = picks.stream()
                .sorted(Comparator.comparing(com.fantasy.bff.generated.espn.model.LeagueDraftPick::getPick))
                .toList();
        List<com.fantasy.bff.generated.espn.model.LeagueDraftPick> run = new ArrayList<>();
        for (var pick : sorted) {
            if (pick.getPick() != run.size() + 1) {
                break;
            }
            run.add(pick);
        }
        Map<Long, Integer> poolIds = crosswalk.poolIds(
                run.stream().map(com.fantasy.bff.generated.espn.model.LeagueDraftPick::getPlayerId).toList());
        List<LeagueDraftPick> made = new ArrayList<>();
        int unmatched = 0;
        for (var pick : run) {
            Integer poolId = poolIds.get(pick.getPlayerId());
            if (poolId == null) {
                unmatched++;
                poolId = -Math.toIntExact(pick.getPlayerId());
            }
            made.add(new LeagueDraftPick(
                    pick.getPick(), pick.getRound(), teamId(leagueId, pick.getTeamId()), poolId));
        }
        if (unmatched > 0) {
            log.info("{} of {} picks in an ESPN league's draft have no counterpart in the pool",
                    unmatched, made.size());
        }
        return made;
    }

    private static LeagueDraftStatus status(StatusEnum status) {
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
