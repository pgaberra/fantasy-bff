package com.fantasy.bff.email;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(OutputCaptureExtension.class)
class ResendPasswordResetEmailSenderTest {

    private static final Instant EXPIRES = Instant.now().plusSeconds(1800);
    private static final String LINK = "https://app/reset-password?token=live-reset-token";

    private static ResendPasswordResetEmailSender sender(String apiKey, String baseUrl, boolean logLinks) {
        return new ResendPasswordResetEmailSender(new EmailProperties(
                "SlapStat <no-reply@slapstat.com>", new EmailProperties.Resend(apiKey, baseUrl, 5000), logLinks));
    }

    @Test
    void doesNotSendOrLogTheLink_whenApiKeyUnsetOutsideALocalRun(CapturedOutput output) {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        try {
            assertThatNoException().isThrownBy(() ->
                    sender("", server.baseUrl(), false).send("u@x.com", LINK, EXPIRES));

            server.verify(0, postRequestedFor(urlPathEqualTo("/emails")));
            assertThat(output).doesNotContain("live-reset-token").contains("was not sent");
        } finally {
            server.stop();
        }
    }

    @Test
    void logsTheLinkInsteadOfSending_onALocalRun(CapturedOutput output) {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        try {
            sender("", server.baseUrl(), true).send("u@x.com", LINK, EXPIRES);

            server.verify(0, postRequestedFor(urlPathEqualTo("/emails")));
            assertThat(output).contains(LINK);
        } finally {
            server.stop();
        }
    }

    @Test
    void postsToResendWithBearerAuth_andNeverLogsTheLink_whenApiKeySet(CapturedOutput output) {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        try {
            server.stubFor(post(urlPathEqualTo("/emails")).willReturn(okJson("{\"id\":\"email-1\"}")));

            sender("re_test_key", server.baseUrl(), true).send("u@x.com", LINK, EXPIRES);

            server.verify(postRequestedFor(urlPathEqualTo("/emails"))
                    .withHeader("Authorization", equalTo("Bearer re_test_key")));
            assertThat(output).doesNotContain("live-reset-token");
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

            assertThatNoException().isThrownBy(() ->
                    sender("re_test_key", server.baseUrl(), false).send("u@x.com", LINK, EXPIRES));
        } finally {
            server.stop();
        }
    }
}
