package com.fantasy.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient yahooFantasyServiceClient(
            @Value("${services.yahoo-fantasy.base-url}") String baseUrl,
            @Value("${services.yahoo-fantasy.timeout-ms}") int timeoutMs,
            @Value("${services.yahoo-fantasy.api-key:}") String apiKey) {
        RestClient.Builder builder = buildRestClientBuilder(baseUrl, timeoutMs);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("X-Internal-Api-Key", apiKey);
        }
        return builder.build();
    }

    @Bean
    public RestClient espnFantasyServiceClient(
            @Value("${services.espn-fantasy.base-url}") String baseUrl,
            @Value("${services.espn-fantasy.timeout-ms}") int timeoutMs,
            @Value("${services.espn-fantasy.api-key:}") String apiKey) {
        RestClient.Builder builder = buildRestClientBuilder(baseUrl, timeoutMs);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("X-Internal-Api-Key", apiKey);
        }
        return builder.build();
    }

    @Bean
    public RestClient databaseServiceClient(
            @Value("${services.database.base-url}") String baseUrl,
            @Value("${services.database.timeout-ms}") int timeoutMs,
            @Value("${services.database.api-key:}") String apiKey) {
        RestClient.Builder builder = buildRestClientBuilder(baseUrl, timeoutMs);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("X-Internal-Api-Key", apiKey);
        }
        return builder.build();
    }

    /**
     * db-service again, with a timeout measured for the one-off player-id remap rather than for
     * a request someone is waiting on. That call rewrites every stored projection in one go, so
     * the ordinary three seconds cuts it off mid-write — and db-service, which does not know the
     * caller has gone, commits anyway.
     */
    @Bean
    public RestClient databaseMigrationClient(
            @Value("${services.database.base-url}") String baseUrl,
            @Value("${services.database.migration-timeout-ms}") int timeoutMs,
            @Value("${services.database.api-key:}") String apiKey) {
        RestClient.Builder builder = buildRestClientBuilder(baseUrl, timeoutMs);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("X-Internal-Api-Key", apiKey);
        }
        return builder.build();
    }

    /**
     * db-service again, with a timeout measured for saving a projection rather than for a call
     * that carries an id and returns a row. The body is the whole pool the user has been editing
     * and db-service writes all of it before answering, so the ordinary three seconds gave up on
     * a save that was still perfectly on its way — and the user, not the service, paid for it.
     *
     * <p>Separate from the migration client because the two are not the same trade: nobody is
     * waiting on the remap, and someone is very much waiting on this.
     */
    @Bean
    public RestClient databaseProjectionClient(
            @Value("${services.database.base-url}") String baseUrl,
            @Value("${services.database.projection-timeout-ms}") int timeoutMs,
            @Value("${services.database.api-key:}") String apiKey) {
        RestClient.Builder builder = buildRestClientBuilder(baseUrl, timeoutMs);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("X-Internal-Api-Key", apiKey);
        }
        return builder.build();
    }

    @Bean
    public RestClient projectionServiceClient(
            @Value("${services.projection.base-url}") String baseUrl,
            @Value("${services.projection.timeout-ms}") int timeoutMs,
            @Value("${services.projection.api-key:}") String apiKey) {
        RestClient.Builder builder = buildRestClientBuilder(baseUrl, timeoutMs);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("X-Internal-Api-Key", apiKey);
        }
        return builder.build();
    }

    /**
     * ESPN's public image CDN, which is where an ESPN-sourced player's headshot lives. It is
     * read straight through {@code /api/v1/players/{id}/headshot} rather than handed to the
     * browser as a URL, so the frontend keeps being given a path relative to this API — the
     * one contract it has about headshots — no matter which platform the pool came from.
     */
    @Bean
    public RestClient espnImageClient(
            @Value("${services.espn-images.base-url}") String baseUrl,
            @Value("${services.espn-images.timeout-ms}") int timeoutMs) {
        return buildRestClientBuilder(baseUrl, timeoutMs).build();
    }

    private RestClient.Builder buildRestClientBuilder(String baseUrl, int timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }
}
