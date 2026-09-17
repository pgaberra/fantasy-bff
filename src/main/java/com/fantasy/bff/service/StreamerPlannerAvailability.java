package com.fantasy.bff.service;

import com.fantasy.bff.config.StreamerPlannerProperties;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/**
 * Whether this environment serves the streamer planner: the one answer its endpoints enforce and
 * {@code GET /api/v1/features} reports.
 *
 * <p>A bean rather than a property read at each call site now that the planner has two services
 * behind it. One of them checking and the other forgetting is exactly the shape of a feature that
 * is off and answers anyway.
 */
@Component
public class StreamerPlannerAvailability {

    private final boolean available;

    public StreamerPlannerAvailability(StreamerPlannerProperties properties) {
        this.available = properties.enabled();
    }

    public boolean available() {
        return available;
    }

    /** Refuses with a 404, so an environment without the planner has no planner routes at all. */
    public void require() {
        if (!available) {
            throw new NoSuchElementException("The streamer planner is not available");
        }
    }
}
