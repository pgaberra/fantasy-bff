package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.ImportProjectionRequest;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writing a whole projection goes through its own RestClient, because that is the only place the
 * longer timeout lives — a save routed back through the ordinary client would silently be back on
 * three seconds, which is what lost a user their save in JAVA-SPRING-BOOT-17.
 *
 * <p>A timeout cannot be asserted without making the suite wait, so this pins the wiring instead:
 * the two clients are pointed at different servers and each call has to arrive at the right one.
 */
class HttpDatabaseServiceClientProjectionClientTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private WireMockServer ordinary;
    private WireMockServer projections;
    private HttpDatabaseServiceClient client;

    @BeforeEach
    void setUp() {
        ordinary = startServer();
        projections = startServer();
        client = new HttpDatabaseServiceClient(
                restClient(ordinary), restClient(ordinary), restClient(projections));
    }

    @AfterEach
    void tearDown() {
        ordinary.stop();
        projections.stop();
    }

    @Test
    void createProjection_goesThroughTheProjectionClient() {
        client.createProjection(USER_ID, new CreateProjectionRequest());

        assertThat(requestsTo(projections)).isEqualTo(1);
        assertThat(requestsTo(ordinary)).isZero();
    }

    @Test
    void updateProjection_goesThroughTheProjectionClient() {
        client.updateProjection(USER_ID, PROJECTION_ID, new UpdateProjectionRequest());

        assertThat(requestsTo(projections)).isEqualTo(1);
        assertThat(requestsTo(ordinary)).isZero();
    }

    /** Importing a shared projection writes the same whole-pool payload as a save of one's own. */
    @Test
    void importProjection_goesThroughTheProjectionClient() {
        client.importProjection(USER_ID, new ImportProjectionRequest());

        assertThat(requestsTo(projections)).isEqualTo(1);
        assertThat(requestsTo(ordinary)).isZero();
    }

    /**
     * The other side of the same wiring: a call that carries an id and returns a row keeps the
     * ordinary client, so raising the save timeout has not quietly raised everything.
     */
    @Test
    void deleteProjection_staysOnTheOrdinaryClient() {
        client.deleteProjection(USER_ID, PROJECTION_ID);

        assertThat(requestsTo(ordinary)).isEqualTo(1);
        assertThat(requestsTo(projections)).isZero();
    }

    private static WireMockServer startServer() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        server.stubFor(post(urlPathMatching("/api/v1/.*")).willReturn(okJson("{}")));
        server.stubFor(put(urlPathMatching("/api/v1/.*")).willReturn(okJson("{}")));
        server.stubFor(get(urlPathMatching("/api/v1/.*")).willReturn(okJson("{}")));
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .delete(urlPathMatching("/api/v1/.*")).willReturn(okJson("{}")));
        return server;
    }

    private static RestClient restClient(WireMockServer server) {
        return RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    private static int requestsTo(WireMockServer server) {
        return server.getAllServeEvents().size();
    }
}
