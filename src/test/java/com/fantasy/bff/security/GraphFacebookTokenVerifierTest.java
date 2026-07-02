package com.fantasy.bff.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphFacebookTokenVerifierTest {

    private static final String APP_ID = "app-123";
    private static final String APP_SECRET = "app-secret";

    private WireMockServer server;
    private GraphFacebookTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        verifier = new GraphFacebookTokenVerifier(APP_ID, APP_SECRET, server.baseUrl());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void verify_withoutConfiguredAppId_throwsIllegalState() {
        GraphFacebookTokenVerifier unconfigured =
                new GraphFacebookTokenVerifier("", APP_SECRET, server.baseUrl());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> unconfigured.verify("any-token"))
                .withMessageContaining("FACEBOOK_APP_ID");
    }

    @Test
    void verify_withoutConfiguredAppSecret_throwsIllegalState() {
        GraphFacebookTokenVerifier unconfigured =
                new GraphFacebookTokenVerifier(APP_ID, "", server.baseUrl());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> unconfigured.verify("any-token"))
                .withMessageContaining("FACEBOOK_APP_SECRET");
    }

    @Test
    void verify_withValidToken_returnsIdentity() {
        stubDebugToken(okJson(
                "{\"data\":{\"app_id\":\"app-123\",\"is_valid\":true,\"user_id\":\"fb-1\"}}"));
        server.stubFor(get(urlPathEqualTo("/me"))
                .withQueryParam("access_token", equalTo("user-token"))
                .willReturn(okJson("{\"id\":\"fb-1\",\"email\":\"user@example.com\"}")));

        FacebookIdentity identity = verifier.verify("user-token");

        assertThat(identity.sub()).isEqualTo("fb-1");
        assertThat(identity.email()).isEqualTo("user@example.com");
        server.verify(getRequestedFor(urlPathEqualTo("/debug_token"))
                .withQueryParam("input_token", equalTo("user-token"))
                .withQueryParam("access_token", equalTo("app-123|app-secret")));
    }

    @Test
    void verify_trimsConfiguredCredentials() {
        GraphFacebookTokenVerifier padded =
                new GraphFacebookTokenVerifier(" app-123\n", " app-secret \n", server.baseUrl());
        stubDebugToken(okJson(
                "{\"data\":{\"app_id\":\"app-123\",\"is_valid\":true,\"user_id\":\"fb-1\"}}"));
        server.stubFor(get(urlPathEqualTo("/me"))
                .willReturn(okJson("{\"id\":\"fb-1\",\"email\":\"user@example.com\"}")));

        FacebookIdentity identity = padded.verify("user-token");

        assertThat(identity.sub()).isEqualTo("fb-1");
        server.verify(getRequestedFor(urlPathEqualTo("/debug_token"))
                .withQueryParam("access_token", equalTo("app-123|app-secret")));
    }

    @Test
    void verify_withInvalidToken_throwsSecurity() {
        stubDebugToken(okJson(
                "{\"data\":{\"app_id\":\"app-123\",\"is_valid\":false,\"user_id\":\"fb-1\"}}"));

        assertThatThrownBy(() -> verifier.verify("user-token"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid Facebook access token");
    }

    @Test
    void verify_withTokenForDifferentApp_throwsSecurity() {
        stubDebugToken(okJson(
                "{\"data\":{\"app_id\":\"someone-else\",\"is_valid\":true,\"user_id\":\"fb-1\"}}"));

        assertThatThrownBy(() -> verifier.verify("user-token"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid Facebook access token");
    }

    @Test
    void verify_whenProfileHasNoEmail_throwsSecurity() {
        stubDebugToken(okJson(
                "{\"data\":{\"app_id\":\"app-123\",\"is_valid\":true,\"user_id\":\"fb-1\"}}"));
        server.stubFor(get(urlPathEqualTo("/me"))
                .willReturn(okJson("{\"id\":\"fb-1\"}")));

        assertThatThrownBy(() -> verifier.verify("user-token"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("email");
    }

    @Test
    void verify_whenGraphApiFails_throwsSecurity() {
        stubDebugToken(aResponse().withStatus(500));

        assertThatThrownBy(() -> verifier.verify("user-token"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Could not verify");
    }

    private void stubDebugToken(com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response) {
        server.stubFor(get(urlPathEqualTo("/debug_token"))
                .withQueryParam("input_token", equalTo("user-token"))
                .willReturn(response));
    }
}
