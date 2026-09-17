package com.fantasy.bff.client;

import com.fantasy.bff.model.downstream.GitHubIssue;

import java.util.List;

public interface GitHubIssueClient {

    /** Opens an issue in the feedback repository. */
    GitHubIssue createIssue(String title, String body, List<String> labels);
}
