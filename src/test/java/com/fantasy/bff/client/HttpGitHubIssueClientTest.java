package com.fantasy.bff.client;

import com.fantasy.bff.config.FeedbackProperties;
import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpGitHubIssueClientTest {

    private static final String ISSUES = "/repos/pgaberra/slapstat-feedback/issues";

    private WireMockServer server;
    private HttpGitHubIssueClient client;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfigs.http11());
        server.start();
        client = new HttpGitHubIssueClient(new FeedbackProperties(true, new FeedbackProperties.Github(
                "github_pat_test", "pgaberra/slapstat-feedback", server.baseUrl(), 5000)));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void opensTheIssueInTheConfiguredRepository_andReturnsItsNumber() {
        server.stubFor(post(urlPathEqualTo(ISSUES))
                .willReturn(okJson("{\"number\": 42, \"html_url\": \"https://github.com/x\", \"state\": \"open\"}")));

        int number = client.createIssue("Draft board freezes", "the body", List.of("bug"));

        assertThat(number).isEqualTo(42);
        server.verify(postRequestedFor(urlPathEqualTo(ISSUES))
                .withHeader("Authorization", equalTo("Bearer github_pat_test"))
                .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28"))
                .withRequestBody(equalToJson(
                        "{\"title\": \"Draft board freezes\", \"body\": \"the body\", \"labels\": [\"bug\"]}")));
    }

    @Test
    void throws_whenGitHubRefusesTheToken() {
        server.stubFor(post(urlPathEqualTo(ISSUES)).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.createIssue("t", "b", List.of("bug")))
                .isInstanceOf(RestClientException.class);
    }
}
