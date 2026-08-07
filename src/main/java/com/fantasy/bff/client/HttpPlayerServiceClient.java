package com.fantasy.bff.client;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.yahoo.model.SyncAcceptedResponse;
import com.fantasy.bff.generated.yahoo.model.SyncRunResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@link PlayerServiceClient} that talks to fantasy-player-service over HTTP.
 *
 * player-service serves the merged read model — NHL-native stat lines plus Yahoo
 * eligible positions. This client owns the reshaping into the frontend-facing
 * {@link SkaterResponse}/{@link GoalieResponse}: NHL-native stat fields default to zero
 * (complete blocks for the UI), and positions come from the Yahoo eligibility set
 * (falling back to the NHL position when a player had no Yahoo match).
 *
 * Uses model classes generated from specs/fantasy-player-service-openapi.yaml —
 * if the player-service API changes, update the spec and re-run ./gradlew generatePlayerClient.
 */
@Component
public class HttpPlayerServiceClient implements PlayerServiceClient {

    private final RestClient restClient;

    public HttpPlayerServiceClient(@Qualifier("yahooFantasyServiceClient") RestClient restClient) {
        this.restClient = restClient;
    }

    private static final ParameterizedTypeReference<List<com.fantasy.bff.generated.yahoo.model.SkaterResponse>>
            SKATER_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<com.fantasy.bff.generated.yahoo.model.GoalieResponse>>
            GOALIE_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<SyncRunResponse>> SYNC_RUN_LIST =
            new ParameterizedTypeReference<>() {
            };

    @Override
    public List<SkaterResponse> getSkaters() {
        List<com.fantasy.bff.generated.yahoo.model.SkaterResponse> response = restClient.get()
                .uri("/api/v1/players/skaters")
                .retrieve()
                .body(SKATER_LIST);
        return response == null ? List.of() : response.stream().map(HttpPlayerServiceClient::toSkater).toList();
    }

    @Override
    public List<GoalieResponse> getGoalies() {
        List<com.fantasy.bff.generated.yahoo.model.GoalieResponse> response = restClient.get()
                .uri("/api/v1/players/goalies")
                .retrieve()
                .body(GOALIE_LIST);
        return response == null ? List.of() : response.stream().map(HttpPlayerServiceClient::toGoalie).toList();
    }

    @Override
    public SyncAcceptedResponse triggerSync() {
        return restClient.post()
                .uri("/api/v1/sync")
                .retrieve()
                .body(SyncAcceptedResponse.class);
    }

    @Override
    public List<SyncRunResponse> getSyncRuns(int limit) {
        List<SyncRunResponse> runs = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/sync/runs").queryParam("limit", limit).build())
                .retrieve()
                .body(SYNC_RUN_LIST);
        return runs == null ? List.of() : runs;
    }

    private static SkaterResponse toSkater(com.fantasy.bff.generated.yahoo.model.SkaterResponse s) {
        int ppg = zero(s.getPowerPlayGoals());
        int ppp = zero(s.getPowerPlayPoints());
        int shg = zero(s.getShorthandedGoals());
        int shp = zero(s.getShorthandedPoints());
        return new SkaterResponse(
                (int) (long) s.getId(),
                s.getFirstName() + " " + s.getLastName(),
                s.getTeamAbbrev(),
                s.getHeadshot(),
                s.getSweaterNumber(),
                toPositions(s.getEligiblePositions(), s.getPosition()),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(
                                zero(s.getGamesPlayed()),
                                toiToSeconds(s.getAvgToi())),
                        new SkaterResponse.ScoringStats(
                                zero(s.getGoals()),
                                zero(s.getAssists()),
                                zero(s.getPoints()),
                                zero(s.getPlusMinus()),
                                zero(s.getPim()),
                                ppg,
                                // The NHL API exposes power-play/shorthanded *points*,
                                // not assists; derive assists as points - goals.
                                ppp - ppg,
                                ppp,
                                shg,
                                shp - shg,
                                shp,
                                zero(s.getGameWinningGoals()),
                                zero(s.getShots()),
                                // NHL shootingPctg is a fraction (0.156); UI wants percent.
                                round1(zero(s.getShootingPctg()) * 100.0),
                                zero(s.getTotalFaceoffWins()),
                                zero(s.getTotalFaceoffLosses()),
                                zero(s.getHits()),
                                zero(s.getBlockedShots()))));
    }

    private static GoalieResponse toGoalie(com.fantasy.bff.generated.yahoo.model.GoalieResponse g) {
        return new GoalieResponse(
                (int) (long) g.getId(),
                g.getFirstName() + " " + g.getLastName(),
                g.getTeamAbbrev(),
                g.getHeadshot(),
                g.getSweaterNumber(),
                new GoalieResponse.Stats(
                        new GoalieResponse.UtilityStats(zero(g.getGamesPlayed())),
                        new GoalieResponse.ScoringStats(
                                zero(g.getGamesStarted()),
                                zero(g.getWins()),
                                zero(g.getLosses()),
                                zero(g.getShutouts()),
                                zero(g.getShotsAgainst()),
                                zero(g.getSaves()),
                                zero(g.getGoalsAgainst()),
                                round2(zero(g.getGoalsAgainstAvg())),
                                // savePctg stays a fraction (0.912) — UI convention.
                                round3(zero(g.getSavePctg())))));
    }

    /** Yahoo eligible positions → frontend enum; falls back to the NHL position if none map. */
    private static Set<SkaterPosition> toPositions(List<String> eligiblePositions, String nhlPosition) {
        Set<SkaterPosition> positions = new LinkedHashSet<>();
        if (eligiblePositions != null) {
            for (String position : eligiblePositions) {
                SkaterPosition mapped = mapFantasyPosition(position);
                if (mapped != null) {
                    positions.add(mapped);
                }
            }
        }
        if (positions.isEmpty()) {
            positions.add(mapNhlPosition(nhlPosition));
        }
        return positions;
    }

    private static SkaterPosition mapFantasyPosition(String position) {
        return switch (position == null ? "" : position.toUpperCase(Locale.ROOT)) {
            case "C" -> SkaterPosition.C;
            case "LW", "L" -> SkaterPosition.LW;
            case "RW", "R" -> SkaterPosition.RW;
            case "D" -> SkaterPosition.D;
            default -> null;
        };
    }

    /** NHL position codes are C/L/R/D; the frontend uses C/LW/RW/D. */
    private static SkaterPosition mapNhlPosition(String positionCode) {
        return switch (positionCode == null ? "" : positionCode) {
            case "L" -> SkaterPosition.LW;
            case "R" -> SkaterPosition.RW;
            case "D" -> SkaterPosition.D;
            default -> SkaterPosition.C;
        };
    }

    /** NHL avgToi is "MM:SS" (e.g. "22:59"); the frontend wants seconds per game. */
    private static int toiToSeconds(String avgToi) {
        if (avgToi == null || avgToi.isBlank()) {
            return 0;
        }
        String[] parts = avgToi.split(":");
        if (parts.length != 2) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private static double zero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
