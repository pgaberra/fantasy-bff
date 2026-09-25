package com.fantasy.bff.client;

import com.fantasy.bff.exception.YahooAccessDeniedException;
import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpYahooServiceClientTest {

    private WireMockServer server;
    private HttpYahooServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfigs.http11());
        server.start();
        client = new HttpYahooServiceClient(RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void completeLink_sendsTheUserAndTheCodeInTheBody() {
        server.stubFor(post(urlPathEqualTo("/api/v1/yahoo/oauth/complete"))
                .withRequestBody(equalToJson("{\"appUserId\":\"user-1\",\"code\":\"link-code\"}"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"connected\":true}")));

        assertThat(client.completeLink("user-1", "link-code").getConnected()).isTrue();
    }

    @Test
    void completeLink_whenAnotherUserStartedTheFlow_surfacesTheConflict() {
        server.stubFor(post(urlPathEqualTo("/api/v1/yahoo/oauth/complete")).willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":409,\"message\":\"started by a different account\"}")));

        assertThatThrownBy(() -> client.completeLink("user-1", "link-code"))
                .isInstanceOfSatisfying(RestClientResponseException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void rosters_readsEachTeamsCurrentPlayersForTheUser() {
        server.stubFor(get(urlPathEqualTo("/api/v1/yahoo/leagues/465.l.1/rosters"))
                .withQueryParam("appUserId", equalTo("user-1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"leagueKey":"465.l.1","teams":[{"teamKey":"465.l.1.t.1","name":"Alpha","mine":true,
                                 "players":[{"playerKey":"465.p.6743","playerId":6743,"selectedPosition":"IR+"}]}]}""")));

        var rosters = client.rosters("user-1", "465.l.1");

        assertThat(rosters.getTeams()).hasSize(1);
        assertThat(rosters.getTeams().getFirst().getTeamKey()).isEqualTo("465.l.1.t.1");
        assertThat(rosters.getTeams().getFirst().getPlayers().getFirst().getPlayerId()).isEqualTo(6743);
        assertThat(rosters.getTeams().getFirst().getPlayers().getFirst().getSelectedPosition()).isEqualTo("IR+");
    }

    @Test
    void rosters_whenYahooRefuses_isARefusal() {
        server.stubFor(get(urlPathEqualTo("/api/v1/yahoo/leagues/465.l.1/rosters")).willReturn(aResponse()
                .withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":403,\"message\":\"Yahoo refused the request: not yours.\"}")));

        assertThatThrownBy(() -> client.rosters("user-1", "465.l.1"))
                .isInstanceOf(YahooAccessDeniedException.class)
                .hasMessage("Yahoo refused the request: not yours.");
    }

    /** yahoo-service's body, as it answered while Yahoo withheld the app's access. */
    @Test
    void aForbiddenFromYahooService_isARefusalInYahoosWords() {
        server.stubFor(get(urlPathEqualTo("/api/v1/yahoo/leagues")).willReturn(aResponse()
                .withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"timestamp":"2026-09-14T17:00:09Z","status":403,"error":"Forbidden",
                         "message":"Yahoo refused the request: This application is not authorized to perform this action."}""")));

        assertThatThrownBy(() -> client.leagues("__service__"))
                .isInstanceOf(YahooAccessDeniedException.class)
                .hasMessage("Yahoo refused the request: This application is not authorized to perform this action.");
    }

    @Test
    void aForbiddenWithoutAMessage_isStillARefusal() {
        server.stubFor(get(urlPathEqualTo("/api/v1/yahoo/leagues/465.l.1/teams")).willReturn(aResponse()
                .withStatus(403)
                .withBody("<html>forbidden</html>")));

        assertThatThrownBy(() -> client.teams("user-1", "465.l.1"))
                .isInstanceOf(YahooAccessDeniedException.class)
                .hasMessage("Yahoo refused the request");
    }

    /** Only a refusal changes: an outage still reaches the advice as a downstream failure. */
    @Test
    void aBadGateway_isStillADownstreamFailure() {
        server.stubFor(get(urlPathEqualTo("/api/v1/yahoo/leagues/465.l.1/settings")).willReturn(aResponse()
                .withStatus(502)));

        assertThatThrownBy(() -> client.settings("user-1", "465.l.1"))
                .isInstanceOf(RestClientResponseException.class);
    }
}
