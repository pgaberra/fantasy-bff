package com.fantasy.bff.security;

import com.fantasy.bff.client.DatabaseServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.jwt.secret=test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
        "security.rate-limit.enabled=true",
        "security.rate-limit.endpoints[/api/v1/auth/login].limit=2",
        "security.rate-limit.endpoints[/api/v1/auth/login].window-seconds=60"
})
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseServiceClient databaseServiceClient;

    @Test
    void rateLimitsLoginAfterConfiguredAttempts() throws Exception {
        when(databaseServiceClient.findUserByEmail(anyString())).thenReturn(Optional.empty());
        String body = "{\"email\":\"attacker@example.com\",\"password\":\"guess123\"}";

        // limit = 2: the first two attempts pass through (401 invalid creds), the third is blocked.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void rateLimitKeyUsesTrustedLastForwardedForHop_notTheSpoofableFirst() throws Exception {
        when(databaseServiceClient.findUserByEmail(anyString())).thenReturn(Optional.empty());
        String body = "{\"email\":\"attacker@example.com\",\"password\":\"guess123\"}";

        // The attacker rotates the FIRST X-Forwarded-For entry each request; the real client IP is
        // the constant last hop (appended by the trusted proxy). All three share one bucket, so the
        // third is blocked — rotating the leading entry no longer buys a fresh bucket.
        mockMvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", "1.1.1.1, 203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", "2.2.2.2, 203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", "3.3.3.3, 203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
