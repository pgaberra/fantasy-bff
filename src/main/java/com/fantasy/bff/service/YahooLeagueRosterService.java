package com.fantasy.bff.service;

import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Who each team in a Yahoo league holds today, after every trade, drop and pickup since the draft. */
@Service
public class YahooLeagueRosterService {

    private final YahooServiceClient yahooServiceClient;

    public YahooLeagueRosterService(YahooServiceClient yahooServiceClient) {
        this.yahooServiceClient = yahooServiceClient;
    }

    /**
     * @return each team's player ids by Yahoo's team key, in Yahoo's team and roster order; bench and
     *     injured reserve are on a roster and so are in here
     */
    public Map<String, List<Integer>> rosters(String appUserId, String leagueKey) {
        var response = yahooServiceClient.rosters(appUserId, leagueKey);
        Map<String, List<Integer>> rosters = new LinkedHashMap<>();
        if (response == null) {
            return rosters;
        }
        for (LeagueRosterTeam team : response.getTeams()) {
            rosters.put(team.getTeamKey(), team.getPlayers().stream()
                    .map(LeagueRosterPlayer::getPlayerId)
                    .filter(Objects::nonNull)
                    .toList());
        }
        return rosters;
    }
}
