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

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    @Bean
    public RestClient yahooFantasyServiceClient(
            @Value("${services.yahoo-fantasy.base-url}") String baseUrl,
            @Value("${services.yahoo-fantasy.timeout-ms}") int timeoutMs,
            InternalApiKeyProperties keys) {
        return buildRestClientBuilder(baseUrl, timeoutMs)
                .defaultHeader(INTERNAL_API_KEY_HEADER, keys.yahooFantasy().apiKey())
                .build();
    }

    @Bean
    public RestClient espnFantasyServiceClient(
            @Value("${services.espn-fantasy.base-url}") String baseUrl,
            @Value("${services.espn-fantasy.timeout-ms}") int timeoutMs,
            InternalApiKeyProperties keys) {
        return buildRestClientBuilder(baseUrl, timeoutMs)
                .defaultHeader(INTERNAL_API_KEY_HEADER, keys.espnFantasy().apiKey())
                .build();
    }

    @Bean
    public RestClient databaseServiceClient(
            @Value("${services.database.base-url}") String baseUrl,
            @Value("${services.database.timeout-ms}") int timeoutMs,
            InternalApiKeyProperties keys) {
        return buildRestClientBuilder(baseUrl, timeoutMs)
                .defaultHeader(INTERNAL_API_KEY_HEADER, keys.database().apiKey())
                .build();
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
            InternalApiKeyProperties keys) {
        return buildRestClientBuilder(baseUrl, timeoutMs)
                .defaultHeader(INTERNAL_API_KEY_HEADER, keys.database().apiKey())
                .build();
    }

    /**
     * db-service again, with a timeout measured for moving a whole projection rather than for a
     * call that carries an id and returns a row. The payload is the entire pool the user has been
     * editing, and db-service reads or writes all of it before the response completes, so the
     * ordinary three seconds gave up on calls that were still perfectly on their way — and the
     * user, not the service, paid for it: a save lost, or a projection that would not open.
     *
     * <p>Separate from the migration client because the two are not the same trade: nobody is
     * waiting on the remap, and someone is very much waiting on these.
     */
    @Bean
    public RestClient databaseProjectionClient(
            @Value("${services.database.base-url}") String baseUrl,
            @Value("${services.database.projection-timeout-ms}") int timeoutMs,
            InternalApiKeyProperties keys) {
        return buildRestClientBuilder(baseUrl, timeoutMs)
                .defaultHeader(INTERNAL_API_KEY_HEADER, keys.database().apiKey())
                .build();
    }

    @Bean
    public RestClient projectionServiceClient(
            @Value("${services.projection.base-url}") String baseUrl,
            @Value("${services.projection.timeout-ms}") int timeoutMs,
            InternalApiKeyProperties keys) {
        return buildRestClientBuilder(baseUrl, timeoutMs)
                .defaultHeader(INTERNAL_API_KEY_HEADER, keys.projection().apiKey())
                .build();
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

    /**
     * Paddle Billing's API, when Paddle is the active payment provider. Unlike every other client
     * here this one leaves the cluster, so it is bearer-authenticated rather than carrying the
     * internal key, and it is pointed at Paddle's sandbox or live host by configuration - the two
     * are separate accounts with separate keys, and mixing them is the easy mistake to make.
     */
    @Bean
    public RestClient paddleApiClient(
            @Value("${payments.paddle.api-base-url:https://sandbox-api.paddle.com}") String baseUrl,
            @Value("${payments.paddle.timeout-ms:10000}") int timeoutMs,
            @Value("${payments.paddle.api-key:}") String apiKey) {
        RestClient.Builder builder = buildRestClientBuilder(baseUrl, timeoutMs);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return builder.build();
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
