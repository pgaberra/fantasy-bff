package com.fantasy.bff.email;

import com.fantasy.bff.config.FeedbackProperties;
import com.fantasy.bff.dto.request.FeedbackType;
import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(OutputCaptureExtension.class)
class ResendFeedbackNotificationEmailSenderTest {

    private static final String ISSUE = "https://github.com/pgaberra/slapstat-feedback/issues/7";

    private WireMockServer server;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfigs.http11());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private ResendFeedbackNotificationEmailSender sender(String apiKey, boolean logLinks) {
        return new ResendFeedbackNotificationEmailSender(
                new EmailProperties("SlapStat <no-reply@slapstat.com>",
                        new EmailProperties.Resend(apiKey, server.baseUrl(), 5000), logLinks),
                new FeedbackProperties(new FeedbackProperties.Github(
                        "github_pat_test", "pgaberra/slapstat-feedback", "https://api.github.com", 5000),
                        "info@slapstat.com"));
    }

    @Test
    void mailsTheTitleAndLinkToTheNotifyAddress() {
        server.stubFor(post(urlPathEqualTo("/emails")).willReturn(okJson("{\"id\":\"email-1\"}")));

        sender("re_test_key", false).send(FeedbackType.FEATURE, "Dark mode", ISSUE);

        server.verify(postRequestedFor(urlPathEqualTo("/emails"))
                .withHeader("Authorization", equalTo("Bearer re_test_key"))
                .withRequestBody(equalToJson("""
                        {"to": ["info@slapstat.com"], "subject": "New feature request: Dark mode"}
                        """, true, true)));
        assertThat(server.getAllServeEvents().getFirst().getRequest().getBodyAsString()).contains(ISSUE);
    }

    // The issue is already filed, so the user's report went through whatever happens to this mail.
    @Test
    void swallowsAndLogs_whenResendFails(CapturedOutput output) {
        server.stubFor(post(urlPathEqualTo("/emails")).willReturn(aResponse().withStatus(500)));

        assertThatNoException().isThrownBy(() -> sender("re_test_key", false).send(FeedbackType.BUG, "t", ISSUE));

        assertThat(output).contains("Failed to send the feedback notification");
    }

    @Test
    void sendsNothingWithoutAKey_andSaysSo(CapturedOutput output) {
        sender("", false).send(FeedbackType.BUG, "t", ISSUE);

        server.verify(0, postRequestedFor(urlPathEqualTo("/emails")));
        assertThat(output).contains("a feedback notification was not sent");
    }
}
