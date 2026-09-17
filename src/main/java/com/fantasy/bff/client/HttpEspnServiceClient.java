package com.fantasy.bff.client;

import com.fantasy.bff.generated.espn.model.AvailablePlayer;
import com.fantasy.bff.generated.espn.model.CredentialStatusResponse;
import com.fantasy.bff.generated.espn.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.espn.model.LeagueTeamsResponse;
import com.fantasy.bff.generated.espn.model.PlayerStatsResponse;
import com.fantasy.bff.generated.espn.model.PlayerSyncStatusResponse;
import com.fantasy.bff.generated.espn.model.SyncAcceptedResponse;
import com.fantasy.bff.generated.espn.model.SaveCredentialsRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

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

    private static final ParameterizedTypeReference<List<AvailablePlayer>> AVAILABLE_PLAYERS =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public HttpEspnServiceClient(@Qualifier("espnFantasyServiceClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<AvailablePlayer> leagueFreeAgents(String appUserId, String leagueId, int limit) {
        List<AvailablePlayer> available = restClient.get()
                .uri(b -> b.path("/api/v1/espn/leagues/{leagueId}/free-agents")
                        .queryParam("appUserId", appUserId)
                        .queryParam("limit", limit)
                        .build(leagueId))
                .retrieve()
                .body(AVAILABLE_PLAYERS);
        return available == null ? List.of() : available;
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
    public LeagueSettingsResponse settings(String appUserId, String leagueId) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/espn/leagues/{leagueId}/settings")
                        .queryParam("appUserId", appUserId).build(leagueId))
                .retrieve()
                .body(LeagueSettingsResponse.class);
    }

    @Override
    public LeagueTeamsResponse teams(String appUserId, String leagueId) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/espn/leagues/{leagueId}/teams")
                        .queryParam("appUserId", appUserId).build(leagueId))
                .retrieve()
                .body(LeagueTeamsResponse.class);
    }

    private static final ParameterizedTypeReference<List<com.fantasy.bff.generated.espn.model.SkaterResponse>>
            SKATER_LIST = new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<com.fantasy.bff.generated.espn.model.GoalieResponse>>
            GOALIE_LIST = new ParameterizedTypeReference<>() {
            };

    @Override
    public List<com.fantasy.bff.generated.espn.model.SkaterResponse> skaters(int season) {
        List<com.fantasy.bff.generated.espn.model.SkaterResponse> skaters = restClient.get()
                .uri(b -> b.path("/api/v1/espn/players/skaters").queryParam("season", season).build())
                .retrieve()
                .body(SKATER_LIST);
        return skaters == null ? List.of() : skaters;
    }

    @Override
    public List<com.fantasy.bff.generated.espn.model.GoalieResponse> goalies(int season) {
        List<com.fantasy.bff.generated.espn.model.GoalieResponse> goalies = restClient.get()
                .uri(b -> b.path("/api/v1/espn/players/goalies").queryParam("season", season).build())
                .retrieve()
                .body(GOALIE_LIST);
        return goalies == null ? List.of() : goalies;
    }

    @Override
    public PlayerSyncStatusResponse lastPlayerSync() {
        return restClient.get()
                .uri("/api/v1/espn/players/sync/latest")
                .retrieve()
                .body(PlayerSyncStatusResponse.class);
    }

    @Override
    public SyncAcceptedResponse triggerPlayerSync() {
        return restClient.post()
                .uri("/api/v1/espn/players/sync")
                .retrieve()
                .body(SyncAcceptedResponse.class);
    }

    @Override
    public PlayerStatsResponse playerStats() {
        return restClient.get()
                .uri("/api/v1/espn/players")
                .retrieve()
                .body(PlayerStatsResponse.class);
    }
}
