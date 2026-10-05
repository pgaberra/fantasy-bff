package com.fantasy.bff.service;

import com.fantasy.bff.config.DraftAnalysisProperties;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/**
 * Whether this environment serves Draft Analysis: its own switch, the AI projection's, since every
 * grade is the model's, and reading a league's draft, which is where the picks come from. The one
 * answer its endpoint enforces and {@code GET /api/v1/features} reports.
 */
@Component
public class DraftAnalysisAvailability {

    private final boolean available;

    public DraftAnalysisAvailability(
            DraftAnalysisProperties properties,
            AiProjectionAvailability aiProjection,
            LeagueDraftSyncAvailability leagueDraftSync) {
        this.available = properties.enabled() && aiProjection.available() && leagueDraftSync.available();
    }

    public boolean available() {
        return available;
    }

    /** Refuses with a 404, so an environment without Draft Analysis has no route for it at all. */
    public void require() {
        if (!available) {
            throw new NoSuchElementException("Draft Analysis is not available");
        }
    }
}
