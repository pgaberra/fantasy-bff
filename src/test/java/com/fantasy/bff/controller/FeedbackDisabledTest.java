package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.GitHubIssueClient;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nothing is set here on purpose: an environment nobody has switched on takes no feedback, and
 * tells the web so.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FeedbackDisabledTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private DatabaseServiceClient databaseServiceClient;
    @MockitoBean private GitHubIssueClient gitHubIssueClient;

    @Test
    void theEndpointAnswers404_andNothingIsFiled() throws Exception {
        String token = jwtTokenValidator.generateToken("11111111-1111-1111-1111-111111111111", "m@example.com");

        mockMvc.perform(post("/api/v1/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"BUG\", \"title\": \"t\", \"description\": \"d\"}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(databaseServiceClient, gitHubIssueClient);
    }

    @Test
    void featuresReportItOff() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value(false));
    }
}
