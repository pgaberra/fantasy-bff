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

    // nhl-service is still probed for its version on /api/v1/versions; player data is
    // now served from player-service (see playerServiceClient below).
    @Bean
    public RestClient nhlServiceClient(
            @Value("${services.nhl.base-url}") String baseUrl,
            @Value("${services.nhl.timeout-ms}") int timeoutMs,
            @Value("${services.nhl.api-key:}") String apiKey) {
        RestClient.Builder builder = buildRestClientBuilder(baseUrl, timeoutMs);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("X-Internal-Api-Key", apiKey);
        }
        return builder.build();
    }

    @Bean
    public RestClient playerServiceClient(
            @Value("${services.player.base-url}") String baseUrl,
            @Value("${services.player.timeout-ms}") int timeoutMs,
            @Value("${services.player.api-key:}") String apiKey) {
        RestClient.Builder builder = buildRestClientBuilder(baseUrl, timeoutMs);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("X-Internal-Api-Key", apiKey);
        }
        return builder.build();
    }

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

    private RestClient buildRestClient(String baseUrl, int timeoutMs) {
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
