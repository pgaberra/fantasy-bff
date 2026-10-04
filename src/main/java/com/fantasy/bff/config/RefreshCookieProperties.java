package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the refresh-token cookie is marked. {@code secure} is on unless the configuration says
 * otherwise: only the dev profile turns it off, because Safari refuses a {@code Secure} cookie set
 * over {@code http://localhost}, and a deployed environment that forgets the setting must keep it.
 */
@ConfigurationProperties(prefix = "security.refresh-cookie")
public record RefreshCookieProperties(Boolean secure) {

    public RefreshCookieProperties {
        secure = secure == null || secure;
    }
}
