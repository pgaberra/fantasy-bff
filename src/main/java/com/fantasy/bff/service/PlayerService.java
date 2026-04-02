package com.fantasy.bff.service;

import com.fantasy.bff.client.NhlServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.exception.DownstreamServiceException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlayerService {

    private final NhlServiceClient nhlServiceClient;

    public PlayerService(NhlServiceClient nhlServiceClient) {
        this.nhlServiceClient = nhlServiceClient;
    }

    public List<SkaterResponse> getSkaters() {
        try {
            return nhlServiceClient.getSkaters();
        } catch (Exception e) {
            throw new DownstreamServiceException("Failed to retrieve skaters from NHL service", e);
        }
    }

    public List<GoalieResponse> getGoalies() {
        try {
            return nhlServiceClient.getGoalies();
        } catch (Exception e) {
            throw new DownstreamServiceException("Failed to retrieve goalies from NHL service", e);
        }
    }
}
