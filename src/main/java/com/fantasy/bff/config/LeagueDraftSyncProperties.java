package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether the draft room may follow a Yahoo league's live draft. Off unless the configuration
 * says otherwise: the feature is new, and an environment that forgets the variable keeps it dark.
 *
 * <p>Not read on its own: {@code LeagueDraftSyncAvailability} combines it with the pool's id space,
 * and that combined answer is what the endpoint enforces and the web is told.
 */
@ConfigurationProperties(prefix = "league-draft-sync")
public record LeagueDraftSyncProperties(Boolean enabled) {

    public LeagueDraftSyncProperties {
        enabled = enabled != null && enabled;
    }
}
