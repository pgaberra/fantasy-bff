package com.fantasy.bff.client;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.nhl.model.GoalieStatsLeaderResponse;
import com.fantasy.bff.generated.nhl.model.GoalieStatsLeadersResponse;
import com.fantasy.bff.generated.nhl.model.SkaterStatsLeaderResponse;
import com.fantasy.bff.generated.nhl.model.SkaterStatsLeadersResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

/**
 * {@link NhlServiceClient} that talks to fantasy-nhl-service over HTTP.
 *
 * The nhl-service serves data in NHL-native shape (NHL API field names); this client
 * owns the reshaping into the frontend-facing {@link SkaterResponse}/{@link GoalieResponse}.
 * Stat fields are nullable upstream (rookies have no stats row) and default to zero here,
 * so the frontend always gets complete stat blocks.
 *
 * Uses model classes generated from specs/fantasy-nhl-service-openapi.yaml —
 * if the nhl-service API changes, update the spec and re-run ./gradlew generateNhlClient.
 */
@Component
public class HttpNhlServiceClient implements NhlServiceClient {

    private final RestClient restClient;
    private final int season;

    public HttpNhlServiceClient(@Qualifier("nhlServiceClient") RestClient restClient,
                                @Value("${services.nhl.season}") int season) {
        this.restClient = restClient;
        this.season = season;
    }

    @Override
    public List<SkaterResponse> getSkaters() {
        SkaterStatsLeadersResponse response = restClient.get()
                .uri("/api/v1/skater-stats-leaders/{season}/{gameType}", season, REGULAR_SEASON)
                .retrieve()
                .body(SkaterStatsLeadersResponse.class);
        if (response == null || response.getSkaters() == null) {
            return List.of();
        }
        return response.getSkaters().stream().map(HttpNhlServiceClient::toSkater).toList();
    }

    @Override
    public List<GoalieResponse> getGoalies() {
        GoalieStatsLeadersResponse response = restClient.get()
                .uri("/api/v1/goalie-stats-leaders/{season}/{gameType}", season, REGULAR_SEASON)
                .retrieve()
                .body(GoalieStatsLeadersResponse.class);
        if (response == null || response.getGoalies() == null) {
            return List.of();
        }
        return response.getGoalies().stream().map(HttpNhlServiceClient::toGoalie).toList();
    }

    private static final int REGULAR_SEASON = 2;

    private static SkaterResponse toSkater(SkaterStatsLeaderResponse s) {
        int ppg = zero(s.getPowerPlayGoals());
        int ppp = zero(s.getPowerPlayPoints());
        int shg = zero(s.getShorthandedGoals());
        int shp = zero(s.getShorthandedPoints());
        return new SkaterResponse(
                (int) (long) s.getId(),
                s.getFirstName() + " " + s.getLastName(),
                s.getTeamAbbrev(),
                s.getHeadshot(),
                Set.of(toPosition(s.getPosition())),
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

    private static GoalieResponse toGoalie(GoalieStatsLeaderResponse g) {
        return new GoalieResponse(
                (int) (long) g.getId(),
                g.getFirstName() + " " + g.getLastName(),
                g.getTeamAbbrev(),
                g.getHeadshot(),
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

    /** NHL position codes are C/L/R/D; the frontend uses C/LW/RW/D. */
    private static SkaterPosition toPosition(String positionCode) {
        return switch (positionCode) {
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
