package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.ServiceVersion;
import com.fantasy.bff.dto.response.VersionsResponse;
import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
        WireMockServer server = new WireMockServer(WireMockConfigs.http11());
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
        WireMockServer dead = new WireMockServer(WireMockConfigs.http11());
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

    @Test
    void servesOneProbeToEveryoneWhoAsksWithinTheCacheWindow_thenProbesAgain() {
        Instant start = Instant.parse("2026-09-16T12:00:00Z");
        Instant[] now = {start};
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now[0];
            }
        };
        VersionService service = new VersionService("1.2.3-bff",
                client(dbServer), client(yahooServer), client(espnServer), client(projectionServer),
                poolFrom("espn"), clock, Duration.ofSeconds(10));

        service.getVersions();
        now[0] = start.plusSeconds(9);
        service.getVersions();
        dbServer.verify(1, getRequestedFor(urlPathEqualTo("/actuator/info")));

        now[0] = start.plusSeconds(10);
        dbServer.stubFor(get(urlPathEqualTo("/actuator/info"))
                .willReturn(okJson("{\"app\":{\"version\":\"1.0.1\"}}")));
        VersionsResponse refreshed = service.getVersions();

        dbServer.verify(2, getRequestedFor(urlPathEqualTo("/actuator/info")));
        assertThat(refreshed.services())
                .filteredOn(version -> version.name().equals("fantasy-db-service"))
                .extracting(ServiceVersion::version)
                .containsExactly("1.0.1");
    }
}
