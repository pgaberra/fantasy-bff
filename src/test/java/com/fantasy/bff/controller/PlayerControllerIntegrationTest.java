package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private PlayerServiceClient playerServiceClient;

    @Test
    void getSkaters_withValidToken_returns200() throws Exception {
        when(playerServiceClient.getSkaters()).thenReturn(List.of(
                new SkaterResponse(1, "Connor McDavid", "EDM",
                        "https://assets.nhle.com/mugs/nhl/20242025/EDM/8478402.png", Set.of(SkaterPosition.C),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(82, 1320),
                                new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1, 8, 348, 18.4, 812, 623, 42, 28)
                        ))
        ));

        String token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        mockMvc.perform(get("/api/v1/players/skaters")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Connor McDavid"))
                .andExpect(jsonPath("$[0].teamAbbrev").value("EDM"))
                .andExpect(jsonPath("$[0].headshot").value("https://assets.nhle.com/mugs/nhl/20242025/EDM/8478402.png"))
                .andExpect(jsonPath("$[0].positions[0]").value("C"));
    }

    @Test
    void getSkaters_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/players/skaters"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSkaters_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/players/skaters")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGoalies_withValidToken_returns200() throws Exception {
        when(playerServiceClient.getGoalies()).thenReturn(List.of(
                new GoalieResponse(101, "Igor Shesterkin", "NYR",
                        "https://assets.nhle.com/mugs/nhl/20242025/NYR/8478048.png",
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(58),
                                new GoalieResponse.ScoringStats(58, 36, 17, 3, 1720, 1565, 155, 2.67, 0.910)
                        ))
        ));

        String token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        mockMvc.perform(get("/api/v1/players/goalies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Igor Shesterkin"));
    }

    @Test
    void getGoalies_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/players/goalies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSkaters_whenServiceFails_returns502() throws Exception {
        when(playerServiceClient.getSkaters()).thenThrow(new RuntimeException("player service down"));

        String token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        mockMvc.perform(get("/api/v1/players/skaters")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DOWNSTREAM_UNAVAILABLE"));
    }
}
