package com.fantasy.bff.service;

import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.PlayerSplitResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.projection.model.GoalieSplitResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterSplitResponse;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import com.fantasy.bff.service.mapping.PlayerIdResolver.Candidate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Who has actually been producing lately.
 *
 * <p>The projection service holds per-game logs and can total a player's stats over a stretch
 * of their team's schedule. Those totals are keyed by NHL id and carry no names, so this joins
 * them to the platform's players — the same match the projections need.
 *
 * <p>Ranges are expressed in team game numbers, so "the last twenty" covers the same stretch of
 * schedule for everyone. A player who missed some of them shows fewer games, which is the point.
 */
@Service
public class PlayerSplitService {

    private final ProjectionServiceClient projectionServiceClient;
    private final PlayerServiceClient playerServiceClient;
    private final PlayerIdResolver resolver;

    public PlayerSplitService(
            ProjectionServiceClient projectionServiceClient,
            PlayerServiceClient playerServiceClient,
            PlayerIdResolver resolver) {
        this.projectionServiceClient = projectionServiceClient;
        this.playerServiceClient = playerServiceClient;
        this.resolver = resolver;
    }

    public List<PlayerSplitResponse> skaterSplits(int season, int lastGames, int limit) {
        Context context = context();
        List<PlayerSplitResponse> splits = new ArrayList<>();
        for (SkaterSplitResponse split : projectionServiceClient.skaterSplits(season, lastGames, limit)) {
            Integer playerId = context.platformId(split.getNhlId());
            if (playerId == null) {
                continue;
            }
            Map<String, Double> stats = new LinkedHashMap<>();
            put(stats, "goals", split.getGoals());
            put(stats, "assists", split.getAssists());
            put(stats, "points", split.getPoints());
            put(stats, "plusMinus", split.getPlusMinus());
            put(stats, "pim", split.getPim());
            put(stats, "ppg", split.getPpGoals());
            put(stats, "ppp", split.getPpPoints());
            put(stats, "shg", split.getShGoals());
            put(stats, "shp", split.getShPoints());
            put(stats, "gwg", split.getGwGoals());
            put(stats, "sog", split.getShots());
            put(stats, "toi", split.getToiSeconds());
            splits.add(response(playerId, context, split.getNhlId(), "skater",
                    split.getGames(), split.getFirstTeamGame(), split.getLastTeamGame(), stats));
        }
        return splits;
    }

    public List<PlayerSplitResponse> goalieSplits(int season, int lastGames, int limit) {
        Context context = context();
        List<PlayerSplitResponse> splits = new ArrayList<>();
        for (GoalieSplitResponse split : projectionServiceClient.goalieSplits(season, lastGames, limit)) {
            Integer playerId = context.platformId(split.getNhlId());
            if (playerId == null) {
                continue;
            }
            Map<String, Double> stats = new LinkedHashMap<>();
            put(stats, "gs", split.getGamesStarted());
            put(stats, "w", split.getWins());
            put(stats, "l", split.getLosses());
            put(stats, "sho", split.getShutouts());
            put(stats, "sa", split.getShotsAgainst());
            put(stats, "sv", split.getSaves());
            put(stats, "ga", split.getGoalsAgainst());
            // Both are absent for a goalie who faced no shots in the range; an absent rate is
            // not the same as a zero one.
            put(stats, "gaa", split.getGoalsAgainstAvg());
            put(stats, "svPct", split.getSavePct());
            put(stats, "toi", split.getToiSeconds());
            splits.add(response(playerId, context, split.getNhlId(), "goalie",
                    split.getGames(), split.getFirstTeamGame(), split.getLastTeamGame(), stats));
        }
        return splits;
    }

    private PlayerSplitResponse response(
            int playerId,
            Context context,
            Integer nhlId,
            String type,
            Integer games,
            Integer firstGame,
            Integer lastGame,
            Map<String, Double> stats) {
        PlayerResponse identity = context.identities().get(nhlId.longValue());
        return new PlayerSplitResponse(
                playerId,
                identity == null ? "" : identity.getFullName(),
                identity == null ? null : identity.getCurrentTeam(),
                type,
                games == null ? 0 : games,
                firstGame,
                lastGame,
                stats);
    }

    private record Context(PlayerIdMapping mapping, Map<Long, PlayerResponse> identities) {
        Integer platformId(Integer nhlId) {
            return nhlId == null ? null : mapping.nhlIdToPlatformId().get(nhlId.longValue());
        }
    }

    private Context context() {
        List<PlayerResponse> nhlPlayers = projectionServiceClient.activePlayers();
        Map<Long, PlayerResponse> identities = new HashMap<>();
        List<Candidate> nhlCandidates = new ArrayList<>();
        for (PlayerResponse player : nhlPlayers) {
            identities.put(player.getNhlId().longValue(), player);
            nhlCandidates.add(new Candidate(
                    player.getNhlId().longValue(),
                    player.getFullName(),
                    player.getCurrentTeam(),
                    player.getSweaterNumber()));
        }

        List<Candidate> platform = new ArrayList<>();
        for (SkaterResponse skater : playerServiceClient.getSkaters()) {
            platform.add(new Candidate(
                    skater.id(), skater.name(), skater.teamAbbrev(), skater.sweaterNumber()));
        }
        for (GoalieResponse goalie : playerServiceClient.getGoalies()) {
            platform.add(new Candidate(
                    goalie.id(), goalie.name(), goalie.teamAbbrev(), goalie.sweaterNumber()));
        }

        return new Context(resolver.resolve(nhlCandidates, platform, Map.of()), identities);
    }

    private static void put(Map<String, Double> target, String key, BigDecimal value) {
        if (value != null) {
            target.put(key, value.doubleValue());
        }
    }

    private static void put(Map<String, Double> target, String key, Integer value) {
        if (value != null) {
            target.put(key, value.doubleValue());
        }
    }
}
