package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.EspnLeagueTeam;
import com.fantasy.bff.dto.response.EspnLeagueTeamsResponse;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.generated.espn.model.LeagueTeam;
import com.fantasy.bff.mapper.EspnLeagueSettingsMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EspnLeagueService {

    private final EspnServiceClient espnServiceClient;
    private final EspnLeagueSettingsMapper mapper;

    public EspnLeagueService(EspnServiceClient espnServiceClient, EspnLeagueSettingsMapper mapper) {
        this.espnServiceClient = espnServiceClient;
        this.mapper = mapper;
    }

    public LeagueProjectionSettingsResponse projectionSettings(String appUserId, String leagueId) {
        return mapper.toProjectionSettings(espnServiceClient.settings(appUserId, leagueId));
    }

    public EspnLeagueTeamsResponse teams(String appUserId, String leagueId) {
        List<EspnLeagueTeam> teams = espnServiceClient.teams(appUserId, leagueId).getTeams().stream()
                .map(team -> new EspnLeagueTeam(team.getName(), Boolean.TRUE.equals(team.getMine())))
                .toList();
        return new EspnLeagueTeamsResponse(teams);
    }
}
