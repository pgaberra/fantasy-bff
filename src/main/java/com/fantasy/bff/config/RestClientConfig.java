package com.fantasy.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient nhlServiceClient(
            @Value("${services.nhl.base-url}") String baseUrl,
            @Value("${services.nhl.timeout-ms}") int timeoutMs) {
        return buildRestClient(baseUrl, timeoutMs);
    }

    @Bean
    public RestClient yahooFantasyServiceClient(
            @Value("${services.yahoo-fantasy.base-url}") String baseUrl,
            @Value("${services.yahoo-fantasy.timeout-ms}") int timeoutMs) {
        return buildRestClient(baseUrl, timeoutMs);
    }

    @Bean
    public RestClient databaseServiceClient(
            @Value("${services.database.base-url}") String baseUrl,
            @Value("${services.database.timeout-ms}") int timeoutMs) {
        return buildRestClient(baseUrl, timeoutMs);
    }

    private RestClient buildRestClient(String baseUrl, int timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
