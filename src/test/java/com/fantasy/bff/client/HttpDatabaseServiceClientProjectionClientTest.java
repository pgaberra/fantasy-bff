package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.CopyProjectionRequest;
import com.fantasy.bff.generated.db.model.ImportProjectionRequest;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moving a whole projection goes through its own RestClient, because that is the only place the
 * longer timeout lives — a call routed back through the ordinary client would silently be back on
 * three seconds, which is what lost a user their save in JAVA-SPRING-BOOT-17 and what stopped a
 * projection opening in JAVA-SPRING-BOOT-1S and 1C.
 *
 * <p>A timeout cannot be asserted without making the suite wait, so this pins the wiring instead:
 * the two clients are pointed at different servers and each call has to arrive at the right one.
 * The negative cases matter as much as the positive ones — this must not quietly become a raise
 * of every call to db-service.
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

    /** Following a shared board reads back the same whole-pool payload as one's own. */
    @Test
    void followShare_goesThroughTheProjectionClient() {
        client.followShare(USER_ID, new ImportProjectionRequest());

        assertThat(requestsTo(projections)).isEqualTo(1);
        assertThat(requestsTo(ordinary)).isZero();
    }

    @Test
    void copyShare_goesThroughTheProjectionClient() {
        client.copyShare(USER_ID, new CopyProjectionRequest());

        assertThat(requestsTo(projections)).isEqualTo(1);
        assertThat(requestsTo(ordinary)).isZero();
    }

    @Test
    void getProjection_goesThroughTheProjectionClient() {
        client.getProjection(USER_ID, PROJECTION_ID);

        assertThat(requestsTo(projections)).isEqualTo(1);
        assertThat(requestsTo(ordinary)).isZero();
    }

    /** The same whole-pool payload, read by someone following a share link. */
    @Test
    void getSharedProjection_goesThroughTheProjectionClient() {
        client.getSharedProjection("share-token");

        assertThat(requestsTo(projections)).isEqualTo(1);
        assertThat(requestsTo(ordinary)).isZero();
    }

    /**
     * The summary list is a read too, but it carries names and dates rather than a pool, so it
     * keeps the three seconds. A slow failure on a small call is worse than a fast one.
     */
    @Test
    void listProjections_staysOnTheOrdinaryClient() {
        client.listProjections(USER_ID);

        assertThat(requestsTo(ordinary)).isEqualTo(1);
        assertThat(requestsTo(projections)).isZero();
    }

    /** And the author's picture behind that token: half a megabyte at most, not a whole pool. */
    @Test
    void findSharedProjectionAuthorAvatar_staysOnTheOrdinaryClient() {
        client.findSharedProjectionAuthorAvatar("share-token");

        assertThat(requestsTo(ordinary)).isEqualTo(1);
        assertThat(requestsTo(projections)).isZero();
    }

    /** Likewise the share token itself: a row about a projection, not the projection. */
    @Test
    void getProjectionShare_staysOnTheOrdinaryClient() {
        client.getProjectionShare(USER_ID, PROJECTION_ID);

        assertThat(requestsTo(ordinary)).isEqualTo(1);
        assertThat(requestsTo(projections)).isZero();
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
        WireMockServer server = new WireMockServer(WireMockConfigs.http11());
        server.start();
        server.stubFor(post(urlPathMatching("/api/v1/.*")).willReturn(okJson("{}")));
        server.stubFor(put(urlPathMatching("/api/v1/.*")).willReturn(okJson("{}")));
        server.stubFor(get(urlPathMatching("/api/v1/.*")).willReturn(okJson("{}")));
        // Registered after the catch-all above: the summary list deserialises into an array, and
        // WireMock lets the most recently added matching stub win.
        server.stubFor(get(urlPathMatching("/api/v1/users/[^/]+/projections")).willReturn(okJson("[]")));
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .delete(urlPathMatching("/api/v1/.*")).willReturn(okJson("{}")));
        return server;
    }

    private static RestClient restClient(WireMockServer server) {
        return RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    private static int requestsTo(WireMockServer server) {
        return server.getAllServeEvents().size();
    }
}
