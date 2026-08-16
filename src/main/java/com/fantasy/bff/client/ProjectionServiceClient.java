package com.fantasy.bff.client;

import com.fantasy.bff.dto.request.GameRange;
import com.fantasy.bff.generated.projection.model.GoalieProjectionResponse;
import com.fantasy.bff.generated.projection.model.GoalieSplitResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.generated.projection.model.SkaterSplitResponse;
import java.util.List;

public interface ProjectionServiceClient {

    List<SkaterProjectionResponse> skaterProjections(int season, String modelVersion);

    List<GoalieProjectionResponse> goalieProjections(int season, String modelVersion);

    /**
     * Player identity — name, team, sweater number — for every player the NHL still lists as
     * active. The projections themselves carry only an NHL id, so this is what makes it
     * possible to work out which platform player a projection belongs to.
     */
    /**
     * @param season the season rookie status is wanted for, or null to leave it out
     */
    List<PlayerResponse> activePlayers(Integer season);

    /**
     * Measured totals over a stretch of a team's schedule — what a player actually did over
     * that stretch, not a forecast.
     */
    List<SkaterSplitResponse> skaterSplits(int season, GameRange range, int limit);

    List<GoalieSplitResponse> goalieSplits(int season, GameRange range, int limit);
}
