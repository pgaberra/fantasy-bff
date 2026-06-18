package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "security.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String, Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? Map.of() : rules;
    }

    public record Rule(int limit, int windowSeconds) {}
}
