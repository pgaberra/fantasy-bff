package com.fantasy.bff.client;

import com.fantasy.bff.generated.espn.model.PlayerSyncResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class HttpEspnServiceClientTest {

    private WireMockServer server;
    private HttpEspnServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        // The two differ only in their timeouts; one WireMock serves both here.
        client = new HttpEspnServiceClient(restClient, restClient);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void triggerPlayerSync_asksEspnServiceToRefreshThePool() {
        server.stubFor(post(urlPathEqualTo("/api/v1/espn/players/sync"))
                .willReturn(okJson("{\"players\":1659,\"syncedAt\":\"2026-08-26T07:45:00Z\"}")));

        PlayerSyncResponse response = client.triggerPlayerSync();

        assertThat(response.getPlayers()).isEqualTo(1659);
        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/espn/players/sync")));
    }
}
