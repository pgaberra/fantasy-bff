package com.fantasy.bff.service;

import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
     * @param players each team's player ids by Yahoo's team key, in Yahoo's team and roster order;
     *     bench and injured reserve are on a roster and so are in here
     * @param reserve the players, on any team, parked today in an injured-reserve or not-active slot,
     *     which holds a player without taking one of the roster's spots, each with that slot's code
     *     (IR, IR+, IR-LT, NA, ...)
     */
    public record Rosters(Map<String, List<Integer>> players, Map<Integer, String> reserve) {
    }

    public Rosters rosters(String appUserId, String leagueKey) {
        var response = yahooServiceClient.rosters(appUserId, leagueKey);
        Map<String, List<Integer>> players = new LinkedHashMap<>();
        Map<Integer, String> reserve = new HashMap<>();
        if (response == null) {
            return new Rosters(players, reserve);
        }
        for (LeagueRosterTeam team : response.getTeams()) {
            players.put(team.getTeamKey(), team.getPlayers().stream()
                    .map(LeagueRosterPlayer::getPlayerId)
                    .filter(Objects::nonNull)
                    .toList());
            for (LeagueRosterPlayer player : team.getPlayers()) {
                String slot = player.getSelectedPosition();
                if (slot != null && LeagueOwnTeam.parked(slot)) {
                    reserve.put(player.getPlayerId(), slot.toUpperCase(Locale.ROOT));
                }
            }
        }
        return new Rosters(players, reserve);
    }
}
