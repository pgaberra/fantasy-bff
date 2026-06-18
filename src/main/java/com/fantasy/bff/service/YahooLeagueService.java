package com.fantasy.bff.service;

import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSummary;
import com.fantasy.bff.mapper.YahooLeagueSettingsMapper;
import org.springframework.stereotype.Service;

@Service
public class YahooLeagueService {

    private final YahooServiceClient yahooServiceClient;

    public YahooLeagueService(YahooServiceClient yahooServiceClient) {
        this.yahooServiceClient = yahooServiceClient;
    }

    public LeagueProjectionSettingsResponse projectionSettings(String appUserId, String leagueKey) {
        LeagueSettingsResponse settings = yahooServiceClient.settings(appUserId, leagueKey);
        Integer numTeams = yahooServiceClient.leagues(appUserId).getLeagues().stream()
                .filter(league -> leagueKey.equals(league.getLeagueKey()))
                .map(LeagueSummary::getNumTeams)
                .findFirst()
                .orElse(null);
        return YahooLeagueSettingsMapper.toProjectionSettings(settings, numTeams);
    }
}
