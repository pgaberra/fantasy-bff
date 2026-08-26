package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Serves the player read model the projections are built from, from whichever
 * {@link PlayerPoolSource} is wired in.
 */
@Service
public class PlayerService {

    private final PlayerPoolSource playerPool;

    public PlayerService(PlayerPoolSource playerPool) {
        this.playerPool = playerPool;
    }

    /**
     * Skaters, highest scoring first, capped at {@code limit} when one is given.
     *
     * <p>The order is what makes a limit meaningful: a caller asking for five wants the five the
     * board opens with, not five arbitrary players. Points rather than fantasy points because the
     * weights that turn stats into fantasy points belong to the caller's league, not here — every
     * consumer re-scores what it gets, and points is a wide enough net that the top of any
     * sensible scoring sits inside it.
     */
    public List<SkaterResponse> getSkaters(Integer limit) {
        try {
            return capped(playerPool.getSkaters().stream()
                    .sorted(Comparator
                            .comparingInt((SkaterResponse skater) -> skater.stats().scoring().points())
                            .reversed()
                            // Ties broken by id so the same request answers the same way twice.
                            .thenComparingInt(SkaterResponse::id))
                    .toList(), limit);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve skaters from player service", e);
        }
    }

    /** Goalies, most wins first (then most saves), capped at {@code limit} when one is given. */
    public List<GoalieResponse> getGoalies(Integer limit) {
        try {
            return capped(playerPool.getGoalies().stream()
                    .sorted(Comparator
                            .comparingInt((GoalieResponse goalie) -> goalie.stats().scoring().w())
                            .thenComparingInt(goalie -> goalie.stats().scoring().sv())
                            .reversed()
                            .thenComparingInt(GoalieResponse::id))
                    .toList(), limit);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve goalies from player service", e);
        }
    }

    /** The whole pool, for the callers that project against every player. */
    public List<SkaterResponse> getSkaters() {
        return getSkaters(null);
    }

    /** The whole pool, for the callers that project against every player. */
    public List<GoalieResponse> getGoalies() {
        return getGoalies(null);
    }

    private static <T> List<T> capped(List<T> players, Integer limit) {
        return limit == null || limit >= players.size() ? players : players.subList(0, limit);
    }

    public Optional<byte[]> getHeadshot(int playerId) {
        try {
            return playerPool.getHeadshot(playerId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve a headshot from player service", e);
        }
    }
}
