package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.ServiceVersion;
import com.fantasy.bff.dto.response.VersionsResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionServiceTest {

    private WireMockServer dbServer;
    private WireMockServer yahooServer;
    private WireMockServer espnServer;
    private WireMockServer projectionServer;

    @BeforeEach
    void setUp() {
        dbServer = startServer("1.0.0");
        yahooServer = startServer("3.0.0");
        espnServer = startServer("0.5.0");
        projectionServer = startServer("0.9.0");
    }

    @AfterEach
    void tearDown() {
        dbServer.stop();
        yahooServer.stop();
        espnServer.stop();
        projectionServer.stop();
    }

    /** Stands in for whichever pool source is wired in; only its name matters here. */
    private static PlayerPoolSource poolFrom(String platform) {
        PlayerPoolSource pool = mock(PlayerPoolSource.class);
        when(pool.platform()).thenReturn(platform);
        return pool;
    }

    private WireMockServer startServer(String version) {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        server.stubFor(get(urlPathEqualTo("/actuator/info"))
                .willReturn(okJson("{\"app\":{\"name\":\"svc\",\"version\":\"" + version + "\"}}")));
        return server;
    }

    private RestClient client(WireMockServer server) {
        return RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    @Test
    void reportsOwnVersionAndEachDownstreamVersionInOrder() {
        VersionService service = new VersionService("1.2.3-bff",
                client(dbServer), client(yahooServer), client(espnServer), client(projectionServer),
                poolFrom("yahoo"));

        VersionsResponse response = service.getVersions();

        assertThat(response.services())
                .extracting(ServiceVersion::name)
                .containsExactly("fantasy-bff", "fantasy-db-service", "fantasy-yahoo-service",
                        "fantasy-espn-service", "fantasy-projection-service");
        assertThat(response.services())
                .extracting(ServiceVersion::version)
                .containsExactly("1.2.3-bff", "1.0.0", "3.0.0", "0.5.0", "0.9.0");
        assertThat(response.services()).allSatisfy(version -> assertThat(version.up()).isTrue());
    }

    /**
     * The whole point of reporting it: the switch is an environment variable, and one that did
     * not take looks exactly like one that was never set.
     */
    @Test
    void reportsWhichPlatformThePoolIsActuallyServedFrom() {
        VersionService service = new VersionService("1.2.3-bff",
                client(dbServer), client(yahooServer), client(espnServer), client(projectionServer),
                poolFrom("espn"));

        assertThat(service.getVersions().playerSource()).isEqualTo("espn");
    }

    @Test
    void marksUnreachableServiceDownWithNoVersion() {
        WireMockServer dead = new WireMockServer(wireMockConfig().dynamicPort());
        dead.start();
        dead.stubFor(get(urlPathEqualTo("/actuator/info")).willReturn(aResponse().withStatus(500)));

        VersionService service = new VersionService("1.2.3-bff",
                client(dead), client(yahooServer), client(espnServer), client(projectionServer),
                poolFrom("yahoo"));

        VersionsResponse response = service.getVersions();
        dead.stop();

        ServiceVersion database = response.services().stream()
                .filter(version -> version.name().equals("fantasy-db-service"))
                .findFirst()
                .orElseThrow();
        assertThat(database.up()).isFalse();
        assertThat(database.version()).isNull();
    }
}
