package com.fantasy.bff.controller;

import com.fantasy.bff.service.StreamerPlannerAvailability;
import com.fantasy.bff.dto.response.FeaturesResponse;
import com.fantasy.bff.service.AiProjectionAvailability;
import com.fantasy.bff.service.LeagueDraftSyncAvailability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What this environment serves, for a client deciding what to offer. Read from configuration
 * alone, so it costs no downstream call and cannot be slowed by one; that is why it is not a
 * field on {@code /api/v1/versions}, which probes every service it reports.
 */
@RestController
@RequestMapping("/api/v1/features")
@Tag(name = "Features", description = "Which features this environment serves")
public class FeatureController {

    private final AiProjectionAvailability aiProjection;
    private final LeagueDraftSyncAvailability leagueDraftSync;
    private final StreamerPlannerAvailability streamerPlanner;

    public FeatureController(
            AiProjectionAvailability aiProjection,
            LeagueDraftSyncAvailability leagueDraftSync,
            StreamerPlannerAvailability streamerPlanner) {
        this.aiProjection = aiProjection;
        this.leagueDraftSync = leagueDraftSync;
        this.streamerPlanner = streamerPlanner;
    }

    @GetMapping
    @Operation(summary = "Get the features this environment serves",
            description = "Public, since pages a signed-out visitor can open describe these features too. "
                    + "The same answer the endpoints behind each feature enforce.")
    @ApiResponse(responseCode = "200", description = "Features retrieved successfully")
    public FeaturesResponse getFeatures() {
        return new FeaturesResponse(
                aiProjection.available(), leagueDraftSync.available(), streamerPlanner.available());
    }
}
