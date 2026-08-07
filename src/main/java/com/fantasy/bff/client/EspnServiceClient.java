package com.fantasy.bff.client;

import com.fantasy.bff.generated.espn.model.CredentialStatusResponse;
import com.fantasy.bff.generated.espn.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.espn.model.LeagueTeamsResponse;

public interface EspnServiceClient {

    CredentialStatusResponse credentialStatus(String appUserId);

    void saveCredentials(String appUserId, String espnS2, String swid);

    void deleteCredentials(String appUserId);

    LeagueSettingsResponse settings(String appUserId, String leagueId);

    LeagueTeamsResponse teams(String appUserId, String leagueId);
}
