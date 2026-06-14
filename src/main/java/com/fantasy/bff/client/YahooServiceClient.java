package com.fantasy.bff.client;

import com.fantasy.bff.generated.yahoo.model.AuthorizeUrlResponse;
import com.fantasy.bff.generated.yahoo.model.ConnectionResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.LeaguesResponse;

public interface YahooServiceClient {

    AuthorizeUrlResponse authorizeUrl(String appUserId);

    ConnectionResponse connection(String appUserId);

    LeaguesResponse leagues(String appUserId);

    LeagueSettingsResponse settings(String appUserId, String leagueKey);
}
