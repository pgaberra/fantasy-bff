package com.fantasy.bff.service;

import com.fantasy.bff.config.RestOfSeasonPresetProperties;
import org.springframework.stereotype.Component;

/**
 * Whether this environment offers the rest of the season as a draft preset: its own switch, and
 * the AI projection's, since the rows are the model's. The one answer {@code source=rest_of_season}
 * and its status endpoint enforce and {@code GET /api/v1/features} reports.
 */
@Component
public class RestOfSeasonPresetAvailability {

    private final boolean available;

    public RestOfSeasonPresetAvailability(
            RestOfSeasonPresetProperties properties, AiProjectionAvailability aiProjection) {
        this.available = properties.enabled() && aiProjection.available();
    }

    public boolean available() {
        return available;
    }
}
