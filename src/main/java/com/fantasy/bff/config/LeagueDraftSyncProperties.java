package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether the draft room may follow a league's live draft, one switch per platform. Each is off
 * unless the configuration says otherwise: an environment that forgets a variable keeps that
 * platform dark, and ESPN's half can stay off where Yahoo's is already on.
 *
 * <p>Not read on its own: {@code LeagueDraftSyncAvailability} combines it with the pool's id space,
 * and that combined answer is what the endpoints enforce and the web is told.
 *
 * @param enabled following a Yahoo league's draft
 * @param espnEnabled following an ESPN league's draft
 */
@ConfigurationProperties(prefix = "league-draft-sync")
public record LeagueDraftSyncProperties(Boolean enabled, Boolean espnEnabled) {

    public LeagueDraftSyncProperties {
        enabled = enabled != null && enabled;
        espnEnabled = espnEnabled != null && espnEnabled;
    }
}
