package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "security.rate-limit")
public record RateLimitProperties(boolean enabled, Rule fallback, Map<String, Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? Map.of() : rules;
        fallback = fallback == null ? new Rule(20, 300) : fallback;
    }

    public record Rule(int limit, int windowSeconds) {}
}
