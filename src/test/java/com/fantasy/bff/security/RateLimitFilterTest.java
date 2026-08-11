package com.fantasy.bff.security;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.SharedPlayer;
import com.fantasy.bff.generated.db.model.SharedProjectionData;
import com.fantasy.bff.generated.db.model.SharedProjectionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.jwt.secret=test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
        "security.rate-limit.enabled=true",
        "security.rate-limit.endpoints[/api/v1/auth/login].limit=2",
        "security.rate-limit.endpoints[/api/v1/auth/login].window-seconds=60",
        "security.rate-limit.endpoints[/api/v1/shared/*].limit=2",
        "security.rate-limit.endpoints[/api/v1/shared/*].window-seconds=60",
        "security.rate-limit.endpoints[/api/v1/shared/*].method=GET",
        "security.rate-limit.endpoints[/api/v1/shared/*/preview].limit=5",
        "security.rate-limit.endpoints[/api/v1/shared/*/preview].window-seconds=60",
        "security.rate-limit.endpoints[/api/v1/shared/*/preview].method=GET"
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

    private static SharedProjectionResponse sharedProjection() {
        ProjectionSettings settings = new ProjectionSettings()
                .scoringType(ProjectionSettings.ScoringTypeEnum.POINTS)
                .statWeights(Map.of("goals", 4.5))
                .activeScoringColumns(List.of("goals"))
                .activeUtilityColumns(List.of("gp"))
                .scaleSettings(Map.of())
                .decimalSettings(Map.of("goals", 0))
                .useDefaultDecimals(true)
                .leagueSize(12);
        SharedPlayer mcDavid = new SharedPlayer()
                .playerId(1).name("Connor McDavid").type(SharedPlayer.TypeEnum.SKATER)
                .rank(1).value(412.5)
                .stats(new PlayerStats().utility(Map.of("gp", 82.0)).scoring(Map.of("goals", 64.0)));
        return new SharedProjectionResponse()
                .token("abc123").name("My league").authorUsername("alex")
                .season(SharedProjectionResponse.SeasonEnum._20262027)
                .data(new SharedProjectionData().settings(settings).players(List.of(mcDavid)));
    }

    @Test
    void rateLimitsThePublicShareReadEvenThoughItIsAGet() throws Exception {
        when(databaseServiceClient.getSharedProjection(anyString())).thenReturn(sharedProjection());

        mockMvc.perform(get("/api/v1/shared/token-a").header("X-Forwarded-For", "198.51.100.1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/token-a").header("X-Forwarded-For", "198.51.100.1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/token-a").header("X-Forwarded-For", "198.51.100.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void everyTokenSharesOneBucket_soWorkingThroughAListStillTripsTheLimit() throws Exception {
        when(databaseServiceClient.getSharedProjection(anyString())).thenReturn(sharedProjection());

        // A caller pulling one token after another would never fill a per-path bucket, which is
        // exactly why the rule is keyed on the pattern.
        mockMvc.perform(get("/api/v1/shared/token-b").header("X-Forwarded-For", "198.51.100.2")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/token-c").header("X-Forwarded-For", "198.51.100.2")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/token-d").header("X-Forwarded-For", "198.51.100.2")).andExpect(status().isTooManyRequests());
    }

    @Test
    void theMoreSpecificPatternWins_soCrawlerTrafficDoesNotEatTheVisitorBudget() throws Exception {
        when(databaseServiceClient.getSharedProjection(anyString())).thenReturn(sharedProjection());

        // Exhaust the /shared/* rule (limit 2)...
        mockMvc.perform(get("/api/v1/shared/token-e").header("X-Forwarded-For", "198.51.100.3")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/token-e").header("X-Forwarded-For", "198.51.100.3")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shared/token-e").header("X-Forwarded-For", "198.51.100.3")).andExpect(status().isTooManyRequests());

        // ...and the preview, which has its own rule, is untouched by it.
        mockMvc.perform(get("/api/v1/shared/token-e/preview").header("X-Forwarded-For", "198.51.100.3")).andExpect(status().isOk());
    }
}
