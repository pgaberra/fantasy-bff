package com.fantasy.bff.client;

import com.fantasy.bff.generated.espn.model.CredentialStatusResponse;
import com.fantasy.bff.generated.espn.model.CredentialValuesResponse;
import com.fantasy.bff.generated.espn.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.espn.model.LeagueTeamsResponse;
import com.fantasy.bff.generated.espn.model.PlayerStatsResponse;

public interface EspnServiceClient {

    CredentialStatusResponse credentialStatus(String appUserId);

    /** The stored cookies themselves, for showing a user their own saved connection. */
    CredentialValuesResponse credentialValues(String appUserId);

    void saveCredentials(String appUserId, String espnS2, String swid);

    void deleteCredentials(String appUserId);

    LeagueSettingsResponse settings(String appUserId, String leagueId);

    LeagueTeamsResponse teams(String appUserId, String leagueId);

    /** Cached ESPN season stat lines for the stats Yahoo does not report. Not user-specific. */
    PlayerStatsResponse playerStats();
}
