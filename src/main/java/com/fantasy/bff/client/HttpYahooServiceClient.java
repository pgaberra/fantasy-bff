package com.fantasy.bff.client;

import com.fantasy.bff.generated.yahoo.model.AuthorizeUrlResponse;
import com.fantasy.bff.generated.yahoo.model.ConnectionResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueTeamsResponse;
import com.fantasy.bff.generated.yahoo.model.LeaguesResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link YahooServiceClient} that talks to fantasy-yahoo-service over HTTP.
 *
 * The yahoo-service serves data in an already-frontend-friendly shape, so the BFF
 * passes the generated models straight through. The app user id (the JWT subject) is
 * forwarded as the {@code appUserId} query param; the service keys each user's Yahoo
 * tokens by it.
 *
 * Uses model classes generated from specs/fantasy-yahoo-service-openapi.yaml —
 * if the yahoo-service API changes, update the spec and re-run ./gradlew generateYahooClient.
 */
@Component
public class HttpYahooServiceClient implements YahooServiceClient {

    private final RestClient restClient;

    public HttpYahooServiceClient(@Qualifier("yahooFantasyServiceClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AuthorizeUrlResponse authorizeUrl(String appUserId) {
        return restClient.post()
                .uri(b -> b.path("/api/v1/yahoo/oauth/authorize-url").queryParam("appUserId", appUserId).build())
                .retrieve()
                .body(AuthorizeUrlResponse.class);
    }

    @Override
    public ConnectionResponse connection(String appUserId) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/yahoo/oauth/connection").queryParam("appUserId", appUserId).build())
                .retrieve()
                .body(ConnectionResponse.class);
    }

    @Override
    public LeaguesResponse leagues(String appUserId) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/yahoo/leagues").queryParam("appUserId", appUserId).build())
                .retrieve()
                .body(LeaguesResponse.class);
    }

    @Override
    public LeagueSettingsResponse settings(String appUserId, String leagueKey) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/yahoo/leagues/{leagueKey}/settings")
                        .queryParam("appUserId", appUserId).build(leagueKey))
                .retrieve()
                .body(LeagueSettingsResponse.class);
    }

    @Override
    public LeagueTeamsResponse teams(String appUserId, String leagueKey) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/yahoo/leagues/{leagueKey}/teams")
                        .queryParam("appUserId", appUserId).build(leagueKey))
                .retrieve()
                .body(LeagueTeamsResponse.class);
    }
}
