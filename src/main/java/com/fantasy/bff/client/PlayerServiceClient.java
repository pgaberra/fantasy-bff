package com.fantasy.bff.client;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.yahoo.model.SyncAcceptedResponse;
import com.fantasy.bff.generated.yahoo.model.SyncRunResponse;

import java.util.List;
import java.util.Optional;

public interface PlayerServiceClient {

    List<SkaterResponse> getSkaters();

    List<GoalieResponse> getGoalies();

    /** The player's headshot thumbnail as PNG bytes, empty when none is stored for them. */
    Optional<byte[]> getHeadshot(int playerId);

    SyncAcceptedResponse triggerSync();

    List<SyncRunResponse> getSyncRuns(int limit);
}
