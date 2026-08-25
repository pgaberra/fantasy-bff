package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import org.springframework.stereotype.Service;

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

    public List<SkaterResponse> getSkaters() {
        try {
            return playerPool.getSkaters();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve skaters from player service", e);
        }
    }

    public List<GoalieResponse> getGoalies() {
        try {
            return playerPool.getGoalies();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve goalies from player service", e);
        }
    }

    public Optional<byte[]> getHeadshot(int playerId) {
        try {
            return playerPool.getHeadshot(playerId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve a headshot from player service", e);
        }
    }
}
