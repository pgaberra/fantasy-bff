package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether this environment serves Draft Analysis. Off unless the configuration says otherwise: the
 * feature is new, and an environment that forgets the variable keeps it dark.
 * {@link com.fantasy.bff.service.DraftAnalysisAvailability} combines it with the switches of what it
 * reads.
 */
@ConfigurationProperties(prefix = "draft-analysis")
public record DraftAnalysisProperties(Boolean enabled) {

    public DraftAnalysisProperties {
        enabled = enabled != null && enabled;
    }
}
