package com.fantasy.bff.client;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.yahoo.model.SyncAcceptedResponse;
import com.fantasy.bff.generated.yahoo.model.SyncRunResponse;
import com.fantasy.bff.generated.yahoo.model.YahooProbeResponse;

import java.util.List;
import java.util.Optional;

public interface PlayerServiceClient {

    /**
     * @param limit how many to ask yahoo-service for — the highest scoring, by its own ordering
     *     — or null for the whole pool.
     */
    List<SkaterResponse> getSkaters(Integer limit);

    default List<SkaterResponse> getSkaters() {
        return getSkaters(null);
    }

    /** @param limit as for {@link #getSkaters(Integer)}; goalies come back by wins. */
    List<GoalieResponse> getGoalies(Integer limit);

    default List<GoalieResponse> getGoalies() {
        return getGoalies(null);
    }

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
     * @param target {@code leagues} to ask whether the account can list its own leagues at all,
     *     ignoring the other inputs; null for a player collection
     */
    YahooProbeResponse probeYahooAccess(
            String gameKey, String season, String leagueKey, String target);
}
