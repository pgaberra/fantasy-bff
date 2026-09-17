package com.fantasy.bff.service;

import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.LeagueDraftPick;
import com.fantasy.bff.dto.response.LeagueDraftResponse;
import com.fantasy.bff.dto.response.LeagueDraftStatus;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import com.fantasy.bff.generated.yahoo.model.LeagueDraftResponse.StatusEnum;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/** A Yahoo league's draft, in the shape the draft room follows it by. */
@Service
public class YahooLeagueDraftService {

    private final YahooServiceClient yahooServiceClient;
    private final LeagueDraftSyncAvailability availability;

    public YahooLeagueDraftService(YahooServiceClient yahooServiceClient, LeagueDraftSyncAvailability availability) {
        this.yahooServiceClient = yahooServiceClient;
        this.availability = availability;
    }

    public LeagueDraftResponse draft(String appUserId, String leagueKey) {
        if (!availability.available()) {
            throw new NoSuchElementException("Following a league's draft is not enabled");
        }
        var draft = yahooServiceClient.draft(appUserId, leagueKey);
        List<LeagueDraftTeam> teams = draft.getTeams().stream()
                .map(team -> new LeagueDraftTeam(
                        team.getTeamKey(), team.getName(), Boolean.TRUE.equals(team.getMine())))
                .toList();
        return new LeagueDraftResponse(
                status(draft.getStatus()),
                Boolean.TRUE.equals(draft.getAuction()),
                teams,
                madePicks(draft.getPicks()));
    }

    /**
     * Yahoo can list the order's empty slots beside the picks made. The board numbers a pick by
     * its place in the list, so only the unbroken run from pick 1 is passed on.
     */
    private static List<LeagueDraftPick> madePicks(
            List<com.fantasy.bff.generated.yahoo.model.LeagueDraftPick> picks) {
        List<LeagueDraftPick> made = new ArrayList<>();
        var sorted = picks.stream()
                .sorted(Comparator.comparing(com.fantasy.bff.generated.yahoo.model.LeagueDraftPick::getPick))
                .toList();
        for (var pick : sorted) {
            int overall = pick.getPick();
            Integer playerId = pick.getPlayerId();
            if (playerId == null || overall != made.size() + 1) {
                break;
            }
            made.add(new LeagueDraftPick(overall, pick.getRound(), pick.getTeamKey(), playerId));
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
