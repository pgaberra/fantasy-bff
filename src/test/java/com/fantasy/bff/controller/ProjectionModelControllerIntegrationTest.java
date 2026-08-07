package com.fantasy.bff.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.generated.projection.model.SkaterSplitResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectionModelControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private ProjectionServiceClient projectionServiceClient;
    @MockitoBean private PlayerServiceClient playerServiceClient;

    private String token;

    @BeforeEach
    void setUp() {
        token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        PlayerResponse mcdavid = new PlayerResponse();
        mcdavid.setNhlId(8478402);
        mcdavid.setFullName("Connor McDavid");
        mcdavid.setCurrentTeam("EDM");
        mcdavid.setSweaterNumber(97);
        mcdavid.setIsActive(true);
        when(projectionServiceClient.activePlayers()).thenReturn(List.of(mcdavid));
        when(playerServiceClient.getSkaters())
                .thenReturn(List.of(new SkaterResponse(5000, "Connor McDavid", "EDM", null, Set.of(), null)));
        when(playerServiceClient.getGoalies()).thenReturn(List.of());

        SkaterProjectionResponse projection = new SkaterProjectionResponse();
        projection.setNhlId(8478402);
        projection.setGamesPlayed(BigDecimal.valueOf(82));
        projection.setGoals(BigDecimal.valueOf(40));
        when(projectionServiceClient.skaterProjections(anyInt(), anyString()))
                .thenReturn(List.of(projection));
        when(projectionServiceClient.goalieProjections(anyInt(), anyString())).thenReturn(List.of());

        SkaterSplitResponse split = new SkaterSplitResponse();
        split.setNhlId(8478402);
        split.setSeason(2025);
        split.setGames(20);
        split.setGoals(13);
        split.setPoints(33);
        split.setFirstTeamGame(63);
        split.setLastTeamGame(82);
        when(projectionServiceClient.skaterSplits(anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(split));
    }

    @Test
    @DisplayName("the seed endpoint requires a signed-in user")
    void seedRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/seed")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("seeds lines under the platform's player id")
    void seedsLines() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/seed").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].playerId").value(5000))
                .andExpect(jsonPath("$.players[0].type").value("skater"))
                .andExpect(jsonPath("$.players[0].stats.scoring.goals").value(40.0))
                .andExpect(jsonPath("$.skaters").value(1))
                .andExpect(jsonPath("$.modelVersion").value("marcel-v2"));
    }

    @Test
    @DisplayName("returns measured totals over a game range, with the player's name")
    void returnsSplits() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?lastGames=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(5000))
                .andExpect(jsonPath("$[0].name").value("Connor McDavid"))
                .andExpect(jsonPath("$[0].games").value(20))
                .andExpect(jsonPath("$[0].firstTeamGame").value(63))
                .andExpect(jsonPath("$[0].stats.points").value(33.0));
    }

    @Test
    @DisplayName("rejects a game range the schedule cannot contain")
    void rejectsImpossibleRange() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?lastGames=200")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
