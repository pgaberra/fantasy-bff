package com.fantasy.bff.email;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ResendPasswordResetEmailSenderTest {

    private static final Instant EXPIRES = Instant.now().plusSeconds(1800);

    @Test
    void doesNotSendOrThrow_whenApiKeyUnset() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        try {
            ResendPasswordResetEmailSender sender = new ResendPasswordResetEmailSender(
                    "", "from@x.com", server.baseUrl(), 5000);

            assertThatNoException().isThrownBy(() ->
                    sender.send("u@x.com", "https://app/reset-password?token=t", EXPIRES));

            server.verify(0, postRequestedFor(urlPathEqualTo("/emails")));
        } finally {
            server.stop();
        }
    }

    @Test
    void postsToResendWithBearerAuth_whenApiKeySet() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        try {
            server.stubFor(post(urlPathEqualTo("/emails")).willReturn(okJson("{\"id\":\"email-1\"}")));
            ResendPasswordResetEmailSender sender = new ResendPasswordResetEmailSender(
                    "re_test_key", "SlapStat <no-reply@slapstat.com>", server.baseUrl(), 5000);

            sender.send("u@x.com", "https://app/reset-password?token=t", EXPIRES);

            server.verify(postRequestedFor(urlPathEqualTo("/emails"))
                    .withHeader("Authorization", equalTo("Bearer re_test_key")));
        } finally {
            server.stop();
        }
    }

    @Test
    void swallowsAndDoesNotThrow_whenResendFails() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        try {
            server.stubFor(post(urlPathEqualTo("/emails")).willReturn(aResponse().withStatus(500)));
            ResendPasswordResetEmailSender sender = new ResendPasswordResetEmailSender(
                    "re_test_key", "from@x.com", server.baseUrl(), 5000);

            assertThatNoException().isThrownBy(() ->
                    sender.send("u@x.com", "https://app/reset-password?token=t", EXPIRES));
        } finally {
            server.stop();
        }
    }
}
