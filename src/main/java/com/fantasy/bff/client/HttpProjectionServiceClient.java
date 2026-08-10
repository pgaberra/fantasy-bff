package com.fantasy.bff.client;

import com.fantasy.bff.dto.request.GameRange;
import com.fantasy.bff.generated.projection.model.GoalieProjectionResponse;
import com.fantasy.bff.generated.projection.model.GoalieSplitResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.generated.projection.model.SkaterSplitResponse;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

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

    private static final ParameterizedTypeReference<List<SkaterSplitResponse>> SKATER_SPLITS =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<GoalieSplitResponse>> GOALIE_SPLITS =
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

    @Override
    public List<SkaterSplitResponse> skaterSplits(int season, GameRange range, int limit) {
        return restClient.get()
                .uri(b -> splits(b, "/api/v1/splits/skaters", season, range, limit))
                .retrieve()
                .body(SKATER_SPLITS);
    }

    @Override
    public List<GoalieSplitResponse> goalieSplits(int season, GameRange range, int limit) {
        return restClient.get()
                .uri(b -> splits(b, "/api/v1/splits/goalies", season, range, limit))
                .retrieve()
                .body(GOALIE_SPLITS);
    }

    /**
     * Unset bounds are left off the query entirely rather than sent as nulls — the service
     * reads an absent bound as "the whole season", and rejects a request that carries both a
     * last_games shorthand and an explicit range.
     */
    private static URI splits(
            UriBuilder builder, String path, int season, GameRange range, int limit) {
        builder.path(path).queryParam("season", season).queryParam("limit", limit);
        if (range.fromGame() != null) {
            builder.queryParam("from_game", range.fromGame());
        }
        if (range.toGame() != null) {
            builder.queryParam("to_game", range.toGame());
        }
        if (range.lastGames() != null) {
            builder.queryParam("last_games", range.lastGames());
        }
        return builder.build();
    }
}
