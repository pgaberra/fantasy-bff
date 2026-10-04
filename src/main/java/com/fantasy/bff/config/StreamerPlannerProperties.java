package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether this environment serves the streamer planner. Off unless the configuration says otherwise:
 * the feature is new, and an environment that forgets the variable keeps it dark. The one answer the
 * planner's endpoints enforce and {@code GET /api/v1/features} reports.
 *
 * <p>{@code myTeamEnabled} adds the user's own team (open lineup slots per night) on top; it means
 * nothing while the planner itself is off.
 */
@ConfigurationProperties(prefix = "streamer-planner")
public record StreamerPlannerProperties(Boolean enabled, Boolean myTeamEnabled) {

    public StreamerPlannerProperties {
        enabled = enabled != null && enabled;
        myTeamEnabled = myTeamEnabled != null && myTeamEnabled;
    }
}
