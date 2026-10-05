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
    private final boolean myTeamAvailable;

    public StreamerPlannerAvailability(StreamerPlannerProperties properties) {
        this.available = properties.enabled();
        this.myTeamAvailable = available && properties.myTeamEnabled();
    }

    public boolean available() {
        return available;
    }

    /** Whether the planner also reads the user's own team; never without the planner itself. */
    public boolean myTeamAvailable() {
        return myTeamAvailable;
    }

    /** Refuses with a 404, so an environment without the planner has no planner routes at all. */
    public void require() {
        if (!available) {
            throw new NoSuchElementException("The streamer planner is not available");
        }
    }

    /** Refuses with a 404 where the own-team read is off. */
    public void requireMyTeam() {
        if (!myTeamAvailable) {
            throw new NoSuchElementException("The streamer planner's own-team view is not available");
        }
    }
}
