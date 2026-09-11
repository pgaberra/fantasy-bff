package com.fantasy.bff.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The shared secret each downstream demands in {@code X-Internal-Api-Key}. Every one of them
 * refuses a call without it, so none is optional: a missing or blank key stops startup here
 * instead of passing the health check and answering users with 502s.
 */
@ConfigurationProperties("services")
@Validated
public record InternalApiKeyProperties(
        @Valid @NotNull Downstream database,
        @Valid @NotNull Downstream yahooFantasy,
        @Valid @NotNull Downstream espnFantasy,
        @Valid @NotNull Downstream projection
) {
    public record Downstream(@NotBlank String apiKey) {
    }
}
