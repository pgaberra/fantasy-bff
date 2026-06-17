package com.fantasy.bff.service;

import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlayerService {

    private final PlayerServiceClient playerServiceClient;

    public PlayerService(PlayerServiceClient playerServiceClient) {
        this.playerServiceClient = playerServiceClient;
    }

    public List<SkaterResponse> getSkaters() {
        try {
            return playerServiceClient.getSkaters();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve skaters from player service", e);
        }
    }

    public List<GoalieResponse> getGoalies() {
        try {
            return playerServiceClient.getGoalies();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve goalies from player service", e);
        }
    }
}
