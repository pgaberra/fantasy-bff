package com.fantasy.bff.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class DownstreamKeyVerifierTest {

    private WireMockServer server;
    private RestClient client;
    private DownstreamKeyVerifier verifier;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        client = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        verifier = new DownstreamKeyVerifier(client, client, client, "configured", "configured", "configured");
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void verify_returnsOk_whenKeyAccepted() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/exists"))
                .willReturn(okJson("{\"exists\":false}")));

        DownstreamKeyVerifier.Result result =
                verifier.verify("db", "DB_INTERNAL_API_KEY", true, client, "/api/v1/users/exists", "email");

        assertThat(result).isEqualTo(DownstreamKeyVerifier.Result.OK);
    }

    @Test
    void verify_returnsKeyRejected_on401() {
        server.stubFor(get(urlPathEqualTo("/ping"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Missing or invalid X-Internal-Api-Key header\"}")));

        DownstreamKeyVerifier.Result result =
                verifier.verify("db", "DB_INTERNAL_API_KEY", true, client, "/ping", "email");

        assertThat(result).isEqualTo(DownstreamKeyVerifier.Result.KEY_REJECTED);
    }

    @Test
    void verify_returnsUnreachable_whenServiceDown() {
        server.stop();

        DownstreamKeyVerifier.Result result =
                verifier.verify("db", "DB_INTERNAL_API_KEY", true, client, "/ping", "email");

        assertThat(result).isEqualTo(DownstreamKeyVerifier.Result.UNREACHABLE);
    }

    @Test
    void verify_returnsUnreachable_onUnexpectedServerError() {
        server.stubFor(get(urlPathEqualTo("/ping")).willReturn(aResponse().withStatus(500)));

        DownstreamKeyVerifier.Result result =
                verifier.verify("db", "DB_INTERNAL_API_KEY", true, client, "/ping", "email");

        assertThat(result).isEqualTo(DownstreamKeyVerifier.Result.UNREACHABLE);
    }

    @Test
    void verify_skipsWhenKeyNotConfigured() {
        DownstreamKeyVerifier.Result result =
                verifier.verify("db", "DB_INTERNAL_API_KEY", false, client, "/ping", "email");

        assertThat(result).isEqualTo(DownstreamKeyVerifier.Result.SKIPPED);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void verify_sendsSentinelAsQueryParam() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/exists")).willReturn(okJson("{}")));

        verifier.verify("db", "DB_INTERNAL_API_KEY", true, client, "/api/v1/users/exists", "email");

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/users/exists"))
                .withQueryParam("email", equalTo("startup-key-check")));
    }
}
