package com.fantasy.bff.client;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.yahoo.model.SyncAcceptedResponse;
import com.fantasy.bff.generated.yahoo.model.SyncRunResponse;
import com.fantasy.bff.generated.yahoo.model.YahooProbeResponse;

import java.util.List;
import java.util.Optional;

public interface PlayerServiceClient {

    List<SkaterResponse> getSkaters();

    List<GoalieResponse> getGoalies();

    /** The player's headshot thumbnail as PNG bytes, empty when none is stored for them. */
    Optional<byte[]> getHeadshot(int playerId);

    SyncAcceptedResponse triggerSync();

    List<SyncRunResponse> getSyncRuns(int limit);

    /**
     * Asks Yahoo whether the service account may read a game's players, and reports the answer
     * rather than throwing. A refusal is the thing being looked for.
     *
     * @param season season start year, or null to send no season filter
     * @param leagueKey ask a league's player collection instead of the game's, or null
     */
    YahooProbeResponse probeYahooAccess(String gameKey, String season, String leagueKey);
}
