package com.fantasy.bff.client;

import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import java.util.List;

public interface ProjectionServiceClient {

    List<SkaterProjectionResponse> skaterProjections(int season, String modelVersion);

    /**
     * Player identity — name, team, sweater number — for every player the NHL still lists as
     * active. The projections themselves carry only an NHL id, so this is what makes it
     * possible to work out which platform player a projection belongs to.
     */
    List<PlayerResponse> activePlayers();
}
