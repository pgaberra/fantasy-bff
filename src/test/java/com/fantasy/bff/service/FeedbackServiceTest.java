package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.GitHubIssueClient;
import com.fantasy.bff.config.FeedbackProperties;
import com.fantasy.bff.dto.request.FeedbackType;
import com.fantasy.bff.dto.request.SendFeedbackRequest;
import com.fantasy.bff.email.FeedbackNotificationEmailSender;
import com.fantasy.bff.model.downstream.GitHubIssue;
import com.fantasy.bff.model.downstream.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final GitHubIssue ISSUE =
            new GitHubIssue(7, "https://github.com/pgaberra/slapstat-feedback/issues/7");
    private static final User USER = new User(USER_ID.toString(), "manager@example.com", "alex", "hash", 0, true);

    @Mock private DatabaseServiceClient databaseServiceClient;
    @Mock private GitHubIssueClient gitHubIssueClient;
    @Mock private FeedbackNotificationEmailSender notificationEmailSender;

    private FeedbackService service() {
        return new FeedbackService(
                new FeedbackProperties(new FeedbackProperties.Github(
                        "github_pat_test", "pgaberra/slapstat-feedback", "https://api.github.com", 5000), "info@slapstat.com"),
                databaseServiceClient, gitHubIssueClient, notificationEmailSender);
    }

    private static SendFeedbackRequest request(FeedbackType type, String title, String description) {
        return new SendFeedbackRequest(type, title, description, "/draft");
    }

    @Test
    void filesABugWithTheReportersEmailAndTheBugLabel() {
        when(databaseServiceClient.findUserById(USER_ID)).thenReturn(USER);
        when(gitHubIssueClient.createIssue(anyString(), anyString(), anyList())).thenReturn(ISSUE);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        service().send(USER_ID.toString(), request(FeedbackType.BUG, "Board freezes", "It froze."));

        verify(gitHubIssueClient).createIssue(eq("Board freezes"), body.capture(), eq(List.of("bug")));
        assertThat(body.getValue())
                .contains("**Bug report**")
                .contains("**Reply to:** ` manager@example.com `")
                .doesNotContain("not verified")
                .contains("**Page:** ` /draft `")
                .contains("```text\nIt froze.\n```");
    }

    @Test
    void aFeatureRequestGetsTheFeatureLabel() {
        when(databaseServiceClient.findUserById(USER_ID)).thenReturn(USER);

        when(gitHubIssueClient.createIssue(anyString(), anyString(), anyList())).thenReturn(ISSUE);

        service().send(USER_ID.toString(), request(FeedbackType.FEATURE, "Dark mode", "Please."));

        verify(gitHubIssueClient).createIssue(eq("Dark mode"), anyString(), eq(List.of("feature")));
    }

    @Test
    void mailsUsTheIssueOnceItIsFiled() {
        when(databaseServiceClient.findUserById(USER_ID)).thenReturn(USER);
        when(gitHubIssueClient.createIssue(anyString(), anyString(), anyList())).thenReturn(ISSUE);

        service().send(USER_ID.toString(), request(FeedbackType.BUG, "Board\nfreezes", "It froze."));

        verify(notificationEmailSender).send(FeedbackType.BUG, "Board freezes", ISSUE.htmlUrl());
    }

    @Test
    void saysSoWhenTheAddressToReplyToIsNotVerified() {
        User unverified = new User(USER_ID.toString(), "someone@example.com", null, "hash", 0, false);

        String body = FeedbackService.body(unverified, request(FeedbackType.BUG, "t", "d"));

        assertThat(body).contains("` someone@example.com ` (not verified)");
    }

    @Test
    void theMessageCannotCloseItsCodeBlock_soMentionsAndReferencesStayInert() {
        String description = "```\n@pgaberra see #12\n````";

        String body = FeedbackService.body(USER, request(FeedbackType.BUG, "t", description));

        assertThat(body).contains("`````text\n" + description + "\n`````\n");
    }

    @Test
    void aTitleIsFlattenedToOneLine() {
        assertThat(FeedbackService.oneLine("  Board\r\nfreezes\t")).isEqualTo("Board freezes");
    }

    @Test
    void aFailedFilingReachesTheCaller() {
        when(databaseServiceClient.findUserById(USER_ID)).thenReturn(USER);
        when(gitHubIssueClient.createIssue(anyString(), anyString(), any())).thenThrow(new RestClientException("down"));

        assertThatThrownBy(() -> service().send(USER_ID.toString(), request(FeedbackType.BUG, "t", "d")))
                .isInstanceOf(RestClientException.class);

        verifyNoInteractions(notificationEmailSender);
    }
}
