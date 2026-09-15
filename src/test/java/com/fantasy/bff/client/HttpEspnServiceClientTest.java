package com.fantasy.bff.client;

import com.fantasy.bff.generated.espn.model.PlayerSyncStatusResponse;
import com.fantasy.bff.generated.espn.model.SyncAcceptedResponse;
import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class HttpEspnServiceClientTest {

    private WireMockServer server;
    private HttpEspnServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfigs.http11());
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
        client = new HttpEspnServiceClient(restClient);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void triggerPlayerSync_asksEspnServiceToStartOne() {
        server.stubFor(post(urlPathEqualTo("/api/v1/espn/players/sync"))
                .willReturn(aResponse().withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"started\"}")));

        SyncAcceptedResponse response = client.triggerPlayerSync();

        assertThat(response.getStatus()).isEqualTo("started");
        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/espn/players/sync")));
    }

    /** How a triggered sync is watched: poll until it stops running and the stamp has moved. */
    @Test
    void lastPlayerSync_readsWhetherOneIsStillRunning() {
        server.stubFor(get(urlPathEqualTo("/api/v1/espn/players/sync/latest"))
                .willReturn(okJson("{\"syncedAt\":\"2026-08-26T07:45:00Z\",\"players\":1659,"
                        + "\"running\":true}")));

        PlayerSyncStatusResponse status = client.lastPlayerSync();

        assertThat(status.getRunning()).isTrue();
        assertThat(status.getPlayers()).isEqualTo(1659);
    }
}
