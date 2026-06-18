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
    private final YahooLeagueSettingsMapper mapper;

    public YahooLeagueService(YahooServiceClient yahooServiceClient, YahooLeagueSettingsMapper mapper) {
        this.yahooServiceClient = yahooServiceClient;
        this.mapper = mapper;
    }

    public LeagueProjectionSettingsResponse projectionSettings(String appUserId, String leagueKey) {
        LeagueSettingsResponse settings = yahooServiceClient.settings(appUserId, leagueKey);
        Integer numTeams = yahooServiceClient.leagues(appUserId).getLeagues().stream()
                .filter(league -> leagueKey.equals(league.getLeagueKey()))
                .findFirst()
                .map(LeagueSummary::getNumTeams)
                .orElse(null);
        return mapper.toProjectionSettings(settings, numTeams);
    }
}
