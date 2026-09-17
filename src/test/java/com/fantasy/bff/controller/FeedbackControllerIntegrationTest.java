package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.GitHubIssueClient;
import com.fantasy.bff.dto.request.FeedbackType;
import com.fantasy.bff.email.FeedbackNotificationEmailSender;
import com.fantasy.bff.model.downstream.GitHubIssue;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FeedbackControllerIntegrationTest extends BaseIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private DatabaseServiceClient databaseServiceClient;
    @MockitoBean private GitHubIssueClient gitHubIssueClient;
    @MockitoBean private FeedbackNotificationEmailSender notificationEmailSender;

    private String token() {
        return jwtTokenValidator.generateToken(USER_ID.toString(), "manager@example.com");
    }

    private static String json(String type, String title, String description, String page) {
        return "{\"type\": \"" + type + "\", \"title\": \"" + title + "\", \"description\": \""
                + description + "\", \"page\": \"" + page + "\"}";
    }

    @Test
    void filesTheReportForTheSignedInAccount() throws Exception {
        when(databaseServiceClient.findUserById(USER_ID))
                .thenReturn(new User(USER_ID.toString(), "manager@example.com", "alex", "hash", 0, true));
        when(gitHubIssueClient.createIssue(anyString(), anyString(), any()))
                .thenReturn(new GitHubIssue(3, "https://github.com/pgaberra/slapstat-feedback/issues/3"));

        mockMvc.perform(post("/api/v1/feedback")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("BUG", "Board freezes", "It froze on pick 3.", "/draft")))
                .andExpect(status().isNoContent());

        verify(gitHubIssueClient).createIssue(
                eq("Board freezes"), contains("manager@example.com"), eq(List.of("bug")));
        verify(notificationEmailSender).send(
                FeedbackType.BUG, "Board freezes", "https://github.com/pgaberra/slapstat-feedback/issues/3");
    }

    @Test
    void refusesASignedOutCaller() throws Exception {
        mockMvc.perform(post("/api/v1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("BUG", "Board freezes", "It froze.", "/draft")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(gitHubIssueClient);
    }

    @Test
    void refusesATitleOverTheCap() throws Exception {
        mockMvc.perform(post("/api/v1/feedback")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("FEATURE", "x".repeat(121), "d", "/draft")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gitHubIssueClient);
    }

    @Test
    void refusesAPageThatCarriesAQueryString() throws Exception {
        mockMvc.perform(post("/api/v1/feedback")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("BUG", "t", "d", "/reset-password?token=live")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gitHubIssueClient);
    }

    @Test
    void answers502_whenGitHubCannotTakeIt() throws Exception {
        when(databaseServiceClient.findUserById(USER_ID))
                .thenReturn(new User(USER_ID.toString(), "manager@example.com", "alex", "hash", 0, true));
        when(gitHubIssueClient.createIssue(anyString(), anyString(), any())).thenThrow(new RestClientException("401"));

        mockMvc.perform(post("/api/v1/feedback")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("BUG", "t", "d", "/draft")))
                .andExpect(status().isBadGateway());
    }
}
