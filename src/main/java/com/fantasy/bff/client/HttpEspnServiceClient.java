package com.fantasy.bff.client;

import com.fantasy.bff.generated.espn.model.CredentialStatusResponse;
import com.fantasy.bff.generated.espn.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.espn.model.LeagueTeamsResponse;
import com.fantasy.bff.generated.espn.model.SaveCredentialsRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link EspnServiceClient} that talks to fantasy-espn-service over HTTP.
 *
 * ESPN has no OAuth: the service reads public leagues with just a league id, and private
 * leagues with the user's stored espn_s2 + SWID cookies. The app user id (JWT subject) is
 * forwarded as the {@code appUserId} query param; the service keys each user's cookies by it.
 *
 * Uses model classes generated from specs/fantasy-espn-service-openapi.yaml —
 * if the espn-service API changes, update the spec and re-run ./gradlew generateEspnClient.
 */
@Component
public class HttpEspnServiceClient implements EspnServiceClient {

    private final RestClient restClient;

    public HttpEspnServiceClient(@Qualifier("espnFantasyServiceClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public CredentialStatusResponse credentialStatus(String appUserId) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/espn/credentials").queryParam("appUserId", appUserId).build())
                .retrieve()
                .body(CredentialStatusResponse.class);
    }

    @Override
    public void saveCredentials(String appUserId, String espnS2, String swid) {
        restClient.put()
                .uri(b -> b.path("/api/v1/espn/credentials").queryParam("appUserId", appUserId).build())
                .body(new SaveCredentialsRequest().espnS2(espnS2).swid(swid))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void deleteCredentials(String appUserId) {
        restClient.delete()
                .uri(b -> b.path("/api/v1/espn/credentials").queryParam("appUserId", appUserId).build())
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public LeagueSettingsResponse settings(String appUserId, String leagueId, int season) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/espn/leagues/{leagueId}/settings")
                        .queryParam("appUserId", appUserId).queryParam("season", season).build(leagueId))
                .retrieve()
                .body(LeagueSettingsResponse.class);
    }

    @Override
    public LeagueTeamsResponse teams(String appUserId, String leagueId, int season) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/espn/leagues/{leagueId}/teams")
                        .queryParam("appUserId", appUserId).queryParam("season", season).build(leagueId))
                .retrieve()
                .body(LeagueTeamsResponse.class);
    }
}
