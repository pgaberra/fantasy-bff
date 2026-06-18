package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "security.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String, Rule> endpoints) {

    public RateLimitProperties {
        endpoints = endpoints == null ? Map.of() : endpoints;
    }

    public record Rule(int limit, int windowSeconds) {}
}
