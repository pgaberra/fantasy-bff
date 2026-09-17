package com.fantasy.bff.client;

import java.util.List;

public interface GitHubIssueClient {

    /** Opens an issue in the feedback repository and returns its number. */
    int createIssue(String title, String body, List<String> labels);
}
