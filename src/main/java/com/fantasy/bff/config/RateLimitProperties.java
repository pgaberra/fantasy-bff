package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

import java.util.Map;

/**
 * Rate-limit rules, keyed by an exact request path or an Ant pattern
 * ({@code /api/v1/shared/*}). Every path a pattern covers shares one bucket per client.
 * {@code emailsPerAddress} caps the account emails one address is sent, per kind of email,
 * whoever asks for them (see {@code EmailSendThrottle}); left unset, there is no such cap.
 */
@ConfigurationProperties(prefix = "security.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String, Rule> endpoints, Rule emailsPerAddress) {

    public RateLimitProperties {
        endpoints = endpoints == null ? Map.of() : endpoints;
    }

    /**
     * @param method the method the rule guards. Defaults to POST, which is what every rule was
     *               before the public reads needed limiting — so an existing rule keeps its
     *               meaning without restating it.
     */
    public record Rule(int limit, int windowSeconds, HttpMethod method) {

        public HttpMethod methodOrDefault() {
            return method == null ? HttpMethod.POST : method;
        }
    }
}
