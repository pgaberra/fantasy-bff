package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether this environment offers the rest of the season as a draft preset. Off unless the
 * configuration says otherwise: the feature is new, and an environment that forgets the variable
 * keeps it dark. {@link com.fantasy.bff.service.RestOfSeasonPresetAvailability} combines it with
 * the AI projection's own switch.
 */
@ConfigurationProperties(prefix = "rest-of-season-preset")
public record RestOfSeasonPresetProperties(Boolean enabled) {

    public RestOfSeasonPresetProperties {
        enabled = enabled != null && enabled;
    }
}
