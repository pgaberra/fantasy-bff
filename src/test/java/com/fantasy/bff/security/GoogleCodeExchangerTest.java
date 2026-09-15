package com.fantasy.bff.security;

import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleCodeExchangerTest {

    private static final String CLIENT_ID = "client-123";
    private static final String CLIENT_SECRET = "client-secret";
    private static final String REDIRECT_URI = "https://slapstat.com/auth/google/callback";

    private WireMockServer server;
    private GoogleCodeExchanger exchanger;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfigs.http11());
        server.start();
        exchanger = new GoogleCodeExchanger(CLIENT_ID, CLIENT_SECRET, tokenUri(), REDIRECT_URI);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private String tokenUri() {
        return server.baseUrl() + "/token";
    }

    @Test
    void exchange_withoutConfiguredClientSecret_throwsIllegalState() {
        GoogleCodeExchanger unconfigured =
                new GoogleCodeExchanger(CLIENT_ID, "", tokenUri(), REDIRECT_URI);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> unconfigured.exchange("auth-code", REDIRECT_URI))
                .withMessageContaining("GOOGLE_CLIENT_SECRET");
    }

    @Test
    void exchange_withUnrecognizedRedirectUri_throwsIllegalArgument() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> exchanger.exchange("auth-code", "https://evil.example/callback"))
                .withMessageContaining("redirect URI");
    }

    @Test
    void exchange_withValidCode_returnsIdTokenAndPostsExpectedForm() {
        server.stubFor(post(urlPathEqualTo("/token"))
                .willReturn(okJson("{\"id_token\":\"the-id-token\",\"access_token\":\"a\",\"expires_in\":3599}")));

        String idToken = exchanger.exchange("auth-code", REDIRECT_URI);

        assertThat(idToken).isEqualTo("the-id-token");
        server.verify(postRequestedFor(urlPathEqualTo("/token"))
                .withRequestBody(containing("grant_type=authorization_code"))
                .withRequestBody(containing("code=auth-code"))
                .withRequestBody(containing("client_id=client-123"))
                .withRequestBody(containing("client_secret=client-secret")));
    }

    @Test
    void exchange_trimsConfiguredCredentials() {
        GoogleCodeExchanger padded =
                new GoogleCodeExchanger(" client-123\n", " client-secret \n", tokenUri(), REDIRECT_URI);
        server.stubFor(post(urlPathEqualTo("/token"))
                .willReturn(okJson("{\"id_token\":\"the-id-token\"}")));

        assertThat(padded.exchange("auth-code", REDIRECT_URI)).isEqualTo("the-id-token");
        server.verify(postRequestedFor(urlPathEqualTo("/token"))
                .withRequestBody(containing("client_id=client-123"))
                .withRequestBody(containing("client_secret=client-secret")));
    }

    @Test
    void exchange_whenGoogleRejectsCode_throwsSecurity() {
        server.stubFor(post(urlPathEqualTo("/token"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));

        assertThatThrownBy(() -> exchanger.exchange("bad-code", REDIRECT_URI))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Could not exchange");
    }

    @Test
    void exchange_whenGoogleUnreachable_throwsSecurity() {
        server.stubFor(post(urlPathEqualTo("/token")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> exchanger.exchange("auth-code", REDIRECT_URI))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Could not exchange");
    }

    @Test
    void exchange_whenResponseHasNoIdToken_throwsSecurity() {
        server.stubFor(post(urlPathEqualTo("/token"))
                .willReturn(okJson("{\"access_token\":\"a\",\"expires_in\":3599}")));

        assertThatThrownBy(() -> exchanger.exchange("auth-code", REDIRECT_URI))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("no ID token");
    }
}
