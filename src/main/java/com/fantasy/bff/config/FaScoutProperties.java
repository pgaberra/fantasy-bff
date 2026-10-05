package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether this environment serves the FA scout. Off unless the configuration says otherwise: the
 * feature is new, and an environment that forgets the variable keeps it dark.
 * {@link com.fantasy.bff.service.FaScoutAvailability} combines it with the AI projection's own
 * switch.
 */
@ConfigurationProperties(prefix = "fa-scout")
public record FaScoutProperties(Boolean enabled) {

    public FaScoutProperties {
        enabled = enabled != null && enabled;
    }
}
