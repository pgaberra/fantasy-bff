package com.fantasy.bff.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DownstreamKeyVerifierTest {

    private WireMockServer server;
    private RestClient client;
    private ApplicationEventPublisher events;
    private DownstreamKeyVerifier verifier;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        client = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        events = mock(ApplicationEventPublisher.class);
        verifier = new DownstreamKeyVerifier(client, client, client, client, events);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private DownstreamKeyVerifier.Check check(String uri) {
        return new DownstreamKeyVerifier.Check("db", "DB_INTERNAL_API_KEY", client, uri);
    }

    @Test
    void checksEveryDownstreamIncludingProjectionService() {
        assertThat(verifier.checks())
                .extracting(DownstreamKeyVerifier.Check::bffEnvVar)
                .containsExactly("DB_INTERNAL_API_KEY", "YAHOO_INTERNAL_API_KEY",
                        "ESPN_INTERNAL_API_KEY", "PROJECTION_INTERNAL_API_KEY");
    }

    @Test
    void verify_returnsOk_whenKeyAccepted() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/exists"))
                .willReturn(okJson("{\"exists\":false}")));

        DownstreamKeyVerifier.Result result = verifier.verify(check("/api/v1/users/exists?email=x"));

        assertThat(result).isEqualTo(DownstreamKeyVerifier.Result.OK);
        verifyNoInteractions(events);
    }

    @Test
    void verify_refusesTraffic_on401() {
        server.stubFor(get(urlPathEqualTo("/ping"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Missing or invalid X-Internal-Api-Key header\"}")));

        DownstreamKeyVerifier.Result result = verifier.verify(check("/ping"));

        assertThat(result).isEqualTo(DownstreamKeyVerifier.Result.KEY_REJECTED);
        ArgumentCaptor<ApplicationEvent> published = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue()).isInstanceOf(AvailabilityChangeEvent.class);
        assertThat(((AvailabilityChangeEvent<?>) published.getValue()).getState())
                .isEqualTo(ReadinessState.REFUSING_TRAFFIC);
    }

    @Test
    void verify_returnsUnreachable_whenServiceDown_andStaysReady() {
        server.stop();

        DownstreamKeyVerifier.Result result = verifier.verify(check("/ping"));

        assertThat(result).isEqualTo(DownstreamKeyVerifier.Result.UNREACHABLE);
        verifyNoInteractions(events);
    }

    @Test
    void verify_returnsUnreachable_onUnexpectedServerError_andStaysReady() {
        server.stubFor(get(urlPathEqualTo("/ping")).willReturn(aResponse().withStatus(500)));

        DownstreamKeyVerifier.Result result = verifier.verify(check("/ping"));

        assertThat(result).isEqualTo(DownstreamKeyVerifier.Result.UNREACHABLE);
        verifyNoInteractions(events);
    }

    @Test
    void verify_sendsSentinelAsQueryParam() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/exists")).willReturn(okJson("{}")));

        verifier.verify(verifier.checks().getFirst());

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/users/exists"))
                .withQueryParam("email", equalTo("startup-key-check")));
    }
}
