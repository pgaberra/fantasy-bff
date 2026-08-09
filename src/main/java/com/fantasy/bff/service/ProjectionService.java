package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.CreateProjectionRequest;
import com.fantasy.bff.dto.request.ProjectionSource;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.ProjectionData;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectionService {

    private static final TypeReference<Map<String, Double>> STAT_MAP = new TypeReference<>() {};

    private final DatabaseServiceClient databaseServiceClient;
    private final PlayerService playerService;
    private final ObjectMapper objectMapper;

    public ProjectionService(DatabaseServiceClient databaseServiceClient,
                             PlayerService playerService,
                             ObjectMapper objectMapper) {
        this.databaseServiceClient = databaseServiceClient;
        this.playerService = playerService;
        this.objectMapper = objectMapper;
    }

    public ProjectionResponse create(UUID userId, CreateProjectionRequest request) {
        ProjectionData data = request.data();
        if (request.source() != null) {
            if (!data.getPlayers().isEmpty()) {
                throw new IllegalArgumentException(
                        "source and data.players are mutually exclusive: omit players to have the "
                                + "server fill them in, or omit source to send your own");
            }
            data.setPlayers(playersFrom(request.source()));
        } else if (data.getPlayers().isEmpty()) {
            throw new IllegalArgumentException("data.players must not be empty unless source is set");
        }
        return databaseServiceClient.createProjection(userId,
                new com.fantasy.bff.generated.db.model.CreateProjectionRequest()
                        .name(request.name())
                        .data(data));
    }

    /**
     * The player rows a new projection starts from. {@code DEFAULT} keeps each player's current
     * stats, {@code BLANK} zeroes them — the same two starting points the client used to build
     * locally and upload.
     */
    private List<PlayerProjection> playersFrom(ProjectionSource source) {
        boolean blank = source == ProjectionSource.BLANK;
        List<SkaterResponse> skaters = playerService.getSkaters();
        List<GoalieResponse> goalies = playerService.getGoalies();

        List<PlayerProjection> players = new ArrayList<>(skaters.size() + goalies.size());
        for (SkaterResponse skater : skaters) {
            players.add(new PlayerProjection()
                    .playerId(skater.id())
                    .type(PlayerProjection.TypeEnum.SKATER)
                    .stats(stats(skater.stats().utility(), skater.stats().scoring(), blank)));
        }
        for (GoalieResponse goalie : goalies) {
            players.add(new PlayerProjection()
                    .playerId(goalie.id())
                    .type(PlayerProjection.TypeEnum.GOALIE)
                    .stats(stats(goalie.stats().utility(), goalie.stats().scoring(), blank)));
        }
        return players;
    }

    /**
     * Converts the typed stat records to the stored map form through Jackson rather than by
     * listing the fields, so the keys stay identical to what the same records serialise to on
     * {@code /api/v1/players/*} — the client's projections are keyed by exactly those names.
     */
    private PlayerStats stats(Object utility, Object scoring, boolean blank) {
        return new PlayerStats()
                .utility(toStatMap(utility, blank))
                .scoring(toStatMap(scoring, blank));
    }

    private Map<String, Double> toStatMap(Object stats, boolean blank) {
        Map<String, Double> values = objectMapper.convertValue(stats, STAT_MAP);
        if (!blank) {
            return values;
        }
        Map<String, Double> zeroed = new LinkedHashMap<>(values.size());
        values.keySet().forEach(key -> zeroed.put(key, 0.0));
        return zeroed;
    }
}
