package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether Who's hot may narrow its board to the players nobody in the user's league holds. Off
 * unless the configuration says otherwise: the feature is new, and an environment that forgets the
 * variable keeps it dark.
 */
@ConfigurationProperties(prefix = "whos-hot.available-filter")
public record WhosHotAvailableFilterProperties(Boolean enabled) {

    public WhosHotAvailableFilterProperties {
        enabled = enabled != null && enabled;
    }
}
