package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.RosteredPlayersResponse;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Who a league's teams hold between them, which is everyone the league does <b>not</b> have
 * available.
 *
 * <p>Asked this way round on purpose. A platform's free-agent list is ranked by the platform and
 * read a capped page at a time, so a player hot enough to top Who's hot but outside the platform's
 * own top few dozen at his position would be missing from it and read as taken. The rosters are
 * the whole of what is taken, and a league of any size holds a few hundred players.
 */
@Service
public class LeagueRosteredPlayersService {

    private final YahooLeagueRosterService yahooRosters;
    private final EspnLeagueRosterService espnRosters;
    private final WhosHotAvailableFilterAvailability availability;

    public LeagueRosteredPlayersService(
            YahooLeagueRosterService yahooRosters,
            EspnLeagueRosterService espnRosters,
            WhosHotAvailableFilterAvailability availability) {
        this.yahooRosters = yahooRosters;
        this.espnRosters = espnRosters;
        this.availability = availability;
    }

    public RosteredPlayersResponse rostered(String userId, PlayerIdSpace platform, String leagueId) {
        availability.require();
        Collection<List<Integer>> teams = platform == PlayerIdSpace.YAHOO
                ? yahooRosters.rosters(userId, leagueId).players().values()
                : espnRosters.rosters(userId, leagueId).players().values();
        TreeSet<Integer> playerIds = new TreeSet<>();
        for (List<Integer> team : teams) {
            for (Integer playerId : team) {
                // An ESPN player the pool cannot place is kept under a negative id: nobody here.
                if (playerId != null && playerId > 0) {
                    playerIds.add(playerId);
                }
            }
        }
        return new RosteredPlayersResponse(List.copyOf(playerIds));
    }
}
