package com.fantasy.bff.service;

import com.fantasy.bff.config.FaScoutProperties;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/**
 * Whether this environment serves the FA scout: its own switch, and the AI projection's, since
 * every line it shows is the model's rest of the season. The one answer its endpoint enforces and
 * {@code GET /api/v1/features} reports.
 */
@Component
public class FaScoutAvailability {

    private final boolean available;

    public FaScoutAvailability(FaScoutProperties properties, AiProjectionAvailability aiProjection) {
        this.available = properties.enabled() && aiProjection.available();
    }

    public boolean available() {
        return available;
    }

    /** Refuses with a 404, so an environment without the scout has no scout routes at all. */
    public void require() {
        if (!available) {
            throw new NoSuchElementException("The FA scout is not available");
        }
    }
}
