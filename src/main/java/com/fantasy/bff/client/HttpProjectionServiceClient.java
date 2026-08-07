package com.fantasy.bff.client;

import com.fantasy.bff.generated.projection.model.GoalieProjectionResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link ProjectionServiceClient} that talks to fantasy-projection-service over HTTP.
 *
 * The service is NHL-id-keyed and league-agnostic — it returns raw projected stat lines
 * ({@code nhl_id} + the projected stats). Mapping NHL ids to the platform's player id and
 * seeding the user's projection happens in the caller (a later slice), not here.
 *
 * Uses model classes generated from specs/fantasy-projection-service-openapi.json — if the
 * service API changes, update the spec and re-run ./gradlew generateProjectionClient.
 */
@Component
public class HttpProjectionServiceClient implements ProjectionServiceClient {

    private static final ParameterizedTypeReference<List<SkaterProjectionResponse>> SKATER_LIST =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<GoalieProjectionResponse>> GOALIE_LIST =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<PlayerResponse>> PLAYER_LIST =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public HttpProjectionServiceClient(@Qualifier("projectionServiceClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<SkaterProjectionResponse> skaterProjections(int season, String modelVersion) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/projections/skaters")
                        .queryParam("season", season)
                        .queryParam("model_version", modelVersion)
                        .build())
                .retrieve()
                .body(SKATER_LIST);
    }

    @Override
    public List<GoalieProjectionResponse> goalieProjections(int season, String modelVersion) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/projections/goalies")
                        .queryParam("season", season)
                        .queryParam("model_version", modelVersion)
                        .build())
                .retrieve()
                .body(GOALIE_LIST);
    }

    @Override
    public List<PlayerResponse> activePlayers() {
        return restClient.get()
                .uri(b -> b.path("/api/v1/players").queryParam("active", true).build())
                .retrieve()
                .body(PLAYER_LIST);
    }
}
