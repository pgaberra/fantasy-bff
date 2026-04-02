package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.List;

/**
 * Configuration properties for security settings.
 */
@ConfigurationProperties(prefix = "security")
@Validated
public record SecurityProperties(List<String> permittedUrls) {
    public SecurityProperties {
        if (permittedUrls == null) {
            permittedUrls = List.of();
        }
    }
}
