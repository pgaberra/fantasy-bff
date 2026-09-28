package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.mapper.EspnLeagueSettingsMapper;
import org.springframework.stereotype.Service;

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
}
