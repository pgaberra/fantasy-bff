package com.fantasy.bff.client;

import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import java.util.List;

public interface ProjectionServiceClient {

    List<SkaterProjectionResponse> skaterProjections(int season, String modelVersion);
}
