package com.fantasy.bff.client;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.yahoo.model.SyncAcceptedResponse;
import com.fantasy.bff.generated.yahoo.model.SyncRunResponse;

import java.util.List;

public interface PlayerServiceClient {

    List<SkaterResponse> getSkaters();

    List<GoalieResponse> getGoalies();

    SyncAcceptedResponse triggerSync();

    List<SyncRunResponse> getSyncRuns(int limit);
}
