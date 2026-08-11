package com.fantasy.bff.service;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.request.GameRange;
import com.fantasy.bff.dto.response.PlayerSplitResponse;
import com.fantasy.bff.generated.projection.model.GoalieSplitResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterSplitResponse;
import com.fantasy.bff.service.PlayerSplitContextProvider.Context;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Who has actually been producing lately.
 *
 * <p>The projection service holds per-game logs and can total a player's stats over a stretch
 * of their team's schedule. Those totals are keyed by NHL id and carry no names, so they are
 * joined to the platform's players through {@link PlayerSplitContextProvider} — the same match
 * the projections need, and cached there because it is the expensive half of serving a range.
 *
 * <p>Ranges are expressed in team game numbers, so "the last twenty" covers the same stretch of
 * schedule for everyone. A player who missed some of them shows fewer games, which is the point.
 */
@Service
public class PlayerSplitService {

    private final ProjectionServiceClient projectionServiceClient;
    private final PlayerSplitContextProvider contextProvider;

    public PlayerSplitService(
            ProjectionServiceClient projectionServiceClient,
            PlayerSplitContextProvider contextProvider) {
        this.projectionServiceClient = projectionServiceClient;
        this.contextProvider = contextProvider;
    }

    public List<PlayerSplitResponse> skaterSplits(int season, GameRange range, int limit) {
        Context context = contextProvider.context();
        List<PlayerSplitResponse> splits = new ArrayList<>();
        for (SkaterSplitResponse split : projectionServiceClient.skaterSplits(season, range, limit)) {
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
            put(stats, "hits", split.getHits());
            put(stats, "blocks", split.getBlocks());
            put(stats, "fw", split.getFaceoffsWon());
            put(stats, "fl", split.getFaceoffsLost());
            put(stats, "hatTricks", split.getHatTricks());
            put(stats, "shifts", split.getShifts());
            put(stats, "toi", split.getToiSeconds());
            // The service reports the totals it measured; the assist, special-teams and rate
            // splits below are arithmetic on those, and are derived here so every client
            // doesn't redo it.
            putDifference(stats, "ppa", split.getPpPoints(), split.getPpGoals());
            putDifference(stats, "sha", split.getShPoints(), split.getShGoals());
            // A league that scores special teams scores one category, power play plus
            // shorthanded, so it is summed rather than reported anywhere.
            putSum(stats, "stpg", split.getPpGoals(), split.getShGoals());
            putSum(stats, "stpa", stats.get("ppa"), stats.get("sha"));
            putSum(stats, "stp", split.getPpPoints(), split.getShPoints());
            // Defencemen points count a player's points only while they are eligible at
            // defence, so a forward's are not a smaller version of the same thing — they are
            // none of it.
            if (context.playsDefence(playerId)) {
                put(stats, "defPoints", split.getPoints());
            }
            putRatio(stats, "shPct", split.getGoals(), split.getShots(), 100.0);
            putRatio(stats, "toiPerGame", split.getToiSeconds(), split.getGames(), 1.0);
            splits.add(response(playerId, context, split.getNhlId(), "skater",
                    split.getGames(), split.getFirstTeamGame(), split.getLastTeamGame(), stats));
        }
        return splits;
    }

    public List<PlayerSplitResponse> goalieSplits(int season, GameRange range, int limit) {
        Context context = contextProvider.context();
        List<PlayerSplitResponse> splits = new ArrayList<>();
        for (GoalieSplitResponse split : projectionServiceClient.goalieSplits(season, range, limit)) {
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
            put(stats, "otl", split.getOtLosses());
            put(stats, "toi", split.getToiSeconds());
            // Both are absent for a goalie who faced no shots in the range; an absent rate is
            // not the same as a zero one.
            put(stats, "gaa", split.getGoalsAgainstAvg());
            put(stats, "svPct", split.getSavePct());
            putWinPct(stats, split.getWins(), split.getLosses(), split.getOtLosses());
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

    private static void putSum(Map<String, Double> target, String key, Integer first, Integer second) {
        if (first != null && second != null) {
            target.put(key, (double) (first + second));
        }
    }

    private static void putSum(Map<String, Double> target, String key, Double first, Double second) {
        if (first != null && second != null) {
            target.put(key, first + second);
        }
    }

    /**
     * Share of decisions won. An overtime loss is a decision like any other, so a goalie who
     * only ever loses in overtime has a win percentage of zero rather than none.
     */
    private static void putWinPct(Map<String, Double> target, Integer wins, Integer losses, Integer otLosses) {
        if (wins == null || losses == null || otLosses == null) {
            return;
        }
        int decisions = wins + losses + otLosses;
        if (decisions > 0) {
            target.put("winPct", Math.round((double) wins / decisions * 1000.0) / 1000.0);
        }
    }

    private static void putDifference(
            Map<String, Double> target, String key, Integer total, Integer part) {
        if (total != null && part != null) {
            target.put(key, (double) (total - part));
        }
    }

    /**
     * A rate is left out when there is nothing to divide by — a skater who took no shots has no
     * shooting percentage, and reporting it as zero would rank them alongside someone who
     * missed every shot they took.
     */
    private static void putRatio(
            Map<String, Double> target, String key, Integer numerator, Integer denominator, double scale) {
        if (numerator != null && denominator != null && denominator != 0) {
            target.put(key, numerator.doubleValue() / denominator * scale);
        }
    }
}
