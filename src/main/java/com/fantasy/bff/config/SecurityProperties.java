package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.List;

/**
 * Configuration properties for security settings.
 */
@ConfigurationProperties(prefix = "security")
@Validated
public record SecurityProperties(List<String> permittedUrls, List<String> corsAllowedOrigins) {
    public SecurityProperties {
        if (permittedUrls == null) {
            permittedUrls = List.of();
        }
        if (corsAllowedOrigins == null) {
            corsAllowedOrigins = List.of();
        }
    }
}
