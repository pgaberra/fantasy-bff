package com.fantasy.bff.client;

import com.fantasy.bff.config.FeedbackProperties;
import com.fantasy.bff.model.downstream.GitHubIssue;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * GitHub's REST API, used for one thing: opening an issue in the feedback repository. A single
 * call does not justify generating a client from GitHub's spec, so this follows the Resend
 * senders instead.
 */
@Component
public class HttpGitHubIssueClient implements GitHubIssueClient {

    private final RestClient gitHubClient;
    private final FeedbackProperties.Github properties;

    public HttpGitHubIssueClient(FeedbackProperties feedback) {
        this.properties = feedback.github();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        this.gitHubClient = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Override
    public GitHubIssue createIssue(String title, String body, List<String> labels) {
        CreatedIssue created = gitHubClient.post()
                .uri("/repos/{owner}/{name}/issues", properties.owner(), properties.name())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", title, "body", body, "labels", labels))
                .retrieve()
                .body(CreatedIssue.class);
        if (created == null) {
            throw new RestClientException("GitHub answered an issue create with no body");
        }
        return new GitHubIssue(created.number(), created.htmlUrl());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreatedIssue(int number, @JsonProperty("html_url") String htmlUrl) {}
}
