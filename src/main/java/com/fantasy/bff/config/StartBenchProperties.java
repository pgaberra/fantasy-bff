package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether this environment serves Start/Bench. Off unless the configuration says otherwise: the
 * feature is new, and an environment that forgets the variable keeps it dark.
 * {@link com.fantasy.bff.service.StartBenchAvailability} combines it with the AI projection's own
 * switch.
 */
@ConfigurationProperties(prefix = "start-bench")
public record StartBenchProperties(Boolean enabled) {

    public StartBenchProperties {
        enabled = enabled != null && enabled;
    }
}
