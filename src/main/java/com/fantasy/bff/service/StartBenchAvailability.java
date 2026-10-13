package com.fantasy.bff.service;

import com.fantasy.bff.config.StartBenchProperties;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/**
 * Whether this environment serves Start/Bench: its own switch, and the AI projection's, since the
 * lineup it suggests is set by the model's line for the day. The one answer its endpoint enforces
 * and {@code GET /api/v1/features} reports.
 */
@Component
public class StartBenchAvailability {

    private final boolean available;

    public StartBenchAvailability(StartBenchProperties properties, AiProjectionAvailability aiProjection) {
        this.available = properties.enabled() && aiProjection.available();
    }

    public boolean available() {
        return available;
    }

    /** Refuses with a 404, so an environment without Start/Bench has no route for it at all. */
    public void require() {
        if (!available) {
            throw new NoSuchElementException("Start/Bench is not available");
        }
    }
}
