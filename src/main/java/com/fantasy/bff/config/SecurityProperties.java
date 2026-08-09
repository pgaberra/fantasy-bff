package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

/**
 * Configuration properties for security settings.
 */
@ConfigurationProperties(prefix = "security")
@Validated
public record SecurityProperties(List<String> permittedUrls, List<String> corsAllowedOrigins,
                                 List<String> adminEmails, boolean projectionModelEnabled) {
    public SecurityProperties {
        if (permittedUrls == null) {
            permittedUrls = List.of();
        }
        if (corsAllowedOrigins == null) {
            corsAllowedOrigins = List.of();
        }
        adminEmails = adminEmails == null ? List.of()
                : adminEmails.stream()
                        .filter(email -> email != null && !email.isBlank())
                        .map(email -> email.trim().toLowerCase(Locale.ROOT))
                        .toList();
    }
}
