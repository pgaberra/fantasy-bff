package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.GitHubIssueClient;
import com.fantasy.bff.config.FeedbackProperties;
import com.fantasy.bff.dto.request.FeedbackType;
import com.fantasy.bff.dto.request.SendFeedbackRequest;
import com.fantasy.bff.email.FeedbackNotificationEmailSender;
import com.fantasy.bff.model.downstream.GitHubIssue;
import com.fantasy.bff.model.downstream.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Files a signed-in user's bug report or feature request as an issue in the private feedback
 * repository, with the account's email address so the reply can go out by mail, and mails us a
 * link to it.
 *
 * <p>What the user typed goes into the body inside a code block, so an {@code @name} pings nobody
 * and a {@code #123} links to nothing. A title cannot notify anyone, so it is only flattened to
 * one line.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    private static final Pattern BACKTICK_RUN = Pattern.compile("`+");

    private final FeedbackProperties properties;
    private final DatabaseServiceClient databaseServiceClient;
    private final GitHubIssueClient gitHubIssueClient;
    private final FeedbackNotificationEmailSender notificationEmailSender;

    public FeedbackService(FeedbackProperties properties, DatabaseServiceClient databaseServiceClient,
                           GitHubIssueClient gitHubIssueClient,
                           FeedbackNotificationEmailSender notificationEmailSender) {
        this.properties = properties;
        this.databaseServiceClient = databaseServiceClient;
        this.gitHubIssueClient = gitHubIssueClient;
        this.notificationEmailSender = notificationEmailSender;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public void send(String userId, SendFeedbackRequest request) {
        if (!properties.enabled()) {
            throw new NoSuchElementException("Feedback is not enabled");
        }
        User user = databaseServiceClient.findUserById(UUID.fromString(userId));
        String title = oneLine(request.title());
        GitHubIssue issue = gitHubIssueClient.createIssue(title, body(user, request), List.of(label(request.type())));
        log.info("Feedback filed as issue #{}", issue.number());
        notificationEmailSender.send(request.type(), title, issue.htmlUrl());
    }

    static String body(User user, SendFeedbackRequest request) {
        String description = request.description().replace("\r\n", "\n").replace('\r', '\n');
        String fence = "`".repeat(Math.max(3, longestBacktickRun(description) + 1));
        String kind = request.type() == FeedbackType.BUG ? "Bug report" : "Feature request";
        return "**" + kind + "** sent from the site.\n\n"
                + "- **Reply to:** " + inlineCode(user.email())
                + (user.emailVerified() ? "" : " (not verified)") + "\n"
                + "- **Account:** " + inlineCode(user.id()) + "\n"
                + "- **Page:** " + (request.page() == null ? "not given" : inlineCode(request.page())) + "\n\n"
                + fence + "text\n" + description + "\n" + fence + "\n";
    }

    static String oneLine(String text) {
        return text.replaceAll("\\p{Cntrl}+", " ").strip();
    }

    private static String label(FeedbackType type) {
        return type == FeedbackType.BUG ? "bug" : "feature";
    }

    private static String inlineCode(String text) {
        String ticks = "`".repeat(longestBacktickRun(text) + 1);
        return ticks + " " + oneLine(text) + " " + ticks;
    }

    private static int longestBacktickRun(String text) {
        int longest = 0;
        Matcher run = BACKTICK_RUN.matcher(text);
        while (run.find()) {
            longest = Math.max(longest, run.group().length());
        }
        return longest;
    }
}
