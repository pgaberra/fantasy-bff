package com.fantasy.bff.client;

import com.fantasy.bff.generated.yahoo.model.AuthorizeUrlResponse;
import com.fantasy.bff.generated.yahoo.model.ConnectionResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueTeamsResponse;
import com.fantasy.bff.generated.yahoo.model.LeaguesResponse;

public interface YahooServiceClient {

    AuthorizeUrlResponse authorizeUrl(String appUserId);

    ConnectionResponse connection(String appUserId);

    /**
     * Claims the Yahoo tokens a finished consent parked under {@code code}, for {@code appUserId}.
     * yahoo-service answers 404 for an unknown, used or expired code and 409 when another user
     * started the flow; both reach the caller as {@code RestClientResponseException}.
     */
    ConnectionResponse completeLink(String appUserId, String code);

    LeaguesResponse leagues(String appUserId);

    LeagueSettingsResponse settings(String appUserId, String leagueKey);

    LeagueTeamsResponse teams(String appUserId, String leagueKey);
}
