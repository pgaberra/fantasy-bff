package com.fantasy.bff.client;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;

import java.util.List;

public interface NhlServiceClient {

    List<SkaterResponse> getSkaters();

    List<GoalieResponse> getGoalies();
}
