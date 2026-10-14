package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether this environment serves Player Scout, the page that says how the model's view of every
 * player in a league has moved since a chosen day, and what the ice says about why. Off unless the
 * configuration says otherwise: the feature is new, and an environment that forgets the variable
 * keeps it dark. {@link com.fantasy.bff.service.PlayerScoutAvailability} combines it with the AI
 * projection's own switch.
 */
@ConfigurationProperties(prefix = "player-scout")
public record PlayerScoutProperties(Boolean enabled) {

    public PlayerScoutProperties {
        enabled = enabled != null && enabled;
    }
}
