package com.fantasy.bff.controller;

import com.fantasy.bff.client.NhlServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("mock")
class PlayerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private NhlServiceClient nhlServiceClient;

    @Test
    void getSkaters_withValidToken_returns200() throws Exception {
        when(nhlServiceClient.getSkaters()).thenReturn(List.of(
                new SkaterResponse("skater", 1, "Connor McDavid", Set.of("C"),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(82, 1320),
                                new SkaterResponse.ScoringStats(64, 89, 33, 36, 22, 38, 1, 0, 8, 348, 18.4, 812, 623, 42, 28)
                        ))
        ));

        String token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        mockMvc.perform(get("/api/v1/players/skaters")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Connor McDavid"))
                .andExpect(jsonPath("$.data[0].type").value("skater"));
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
        when(nhlServiceClient.getGoalies()).thenReturn(List.of(
                new GoalieResponse("goalie", 101, "Igor Shesterkin",
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(58),
                                new GoalieResponse.ScoringStats(58, 36, 17, 3, 1720, 1565, 155, 2.67, 0.910)
                        ))
        ));

        String token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        mockMvc.perform(get("/api/v1/players/goalies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Igor Shesterkin"))
                .andExpect(jsonPath("$.data[0].type").value("goalie"));
    }

    @Test
    void getGoalies_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/players/goalies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSkaters_whenServiceFails_returns502() throws Exception {
        when(nhlServiceClient.getSkaters()).thenThrow(new RuntimeException("NHL service down"));

        String token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        mockMvc.perform(get("/api/v1/players/skaters")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DOWNSTREAM_UNAVAILABLE"));
    }
}
