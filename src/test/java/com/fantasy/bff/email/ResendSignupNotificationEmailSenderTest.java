package com.fantasy.bff.email;

import com.fantasy.bff.config.SignupNotificationProperties;
import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

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
class ResendSignupNotificationEmailSenderTest {

    private static final String USER = "newcomer@example.com";

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

    private ResendSignupNotificationEmailSender sender(String apiKey, String notifyEmail, Executor executor) {
        return new ResendSignupNotificationEmailSender(
                new EmailProperties("SlapStat <no-reply@slapstat.com>",
                        new EmailProperties.Resend(apiKey, server.baseUrl(), 5000), false),
                new SignupNotificationProperties(notifyEmail),
                executor);
    }

    @Test
    void mailsTheNewUsersAddressAndMethodToTheOwner() {
        server.stubFor(post(urlPathEqualTo("/emails")).willReturn(okJson("{\"id\":\"email-1\"}")));

        sender("re_test_key", "owner@example.com", Runnable::run).send(USER, SignupMethod.GOOGLE);

        server.verify(postRequestedFor(urlPathEqualTo("/emails"))
                .withHeader("Authorization", equalTo("Bearer re_test_key"))
                .withRequestBody(equalToJson("""
                        {"to": ["owner@example.com"], "subject": "New SlapStat user (Google)"}
                        """, true, true)));
        assertThat(server.getAllServeEvents().getFirst().getRequest().getBodyAsString())
                .contains(USER)
                .contains("Sign-up method: Google");
    }

    @Test
    void sendsOffTheCallersThread() {
        List<Runnable> queued = new ArrayList<>();

        sender("re_test_key", "owner@example.com", queued::add).send(USER, SignupMethod.EMAIL);

        server.verify(0, postRequestedFor(urlPathEqualTo("/emails")));
        assertThat(queued).hasSize(1);
    }

    // The account already exists, so the sign-up went through whatever happens to this mail.
    @Test
    void swallowsAndLogs_whenResendFails_withoutLoggingTheUsersAddress(CapturedOutput output) {
        server.stubFor(post(urlPathEqualTo("/emails")).willReturn(aResponse().withStatus(500)));

        assertThatNoException().isThrownBy(() ->
                sender("re_test_key", "owner@example.com", Runnable::run).send(USER, SignupMethod.FACEBOOK));

        assertThat(output).contains("Failed to send the sign-up notification").doesNotContain(USER);
    }

    @Test
    void swallowsAndLogs_whenTheTaskCannotStart(CapturedOutput output) {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("no threads");
        };

        assertThatNoException().isThrownBy(() ->
                sender("re_test_key", "owner@example.com", rejecting).send(USER, SignupMethod.EMAIL));

        assertThat(output).contains("Could not start sending the sign-up notification");
    }

    @Test
    void isOffWithoutARecipient_andSaysSoOnceAtStartup(CapturedOutput output) {
        ResendSignupNotificationEmailSender sender = sender("re_test_key", "", Runnable::run);
        sender.send(USER, SignupMethod.EMAIL);

        server.verify(0, postRequestedFor(urlPathEqualTo("/emails")));
        assertThat(output).containsOnlyOnce("Sign-up notifications are off");
    }

    @Test
    void sendsNothingWithoutAKey_andSaysSo(CapturedOutput output) {
        sender("", "owner@example.com", Runnable::run).send(USER, SignupMethod.EMAIL);

        server.verify(0, postRequestedFor(urlPathEqualTo("/emails")));
        assertThat(output).contains("a sign-up notification was not sent");
    }
}
