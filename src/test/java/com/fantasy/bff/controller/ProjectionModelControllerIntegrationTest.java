package com.fantasy.bff.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.request.GameRange;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.projection-model-enabled=true",
        // The id mapping is cached in a singleton, so without this the first test's stubs would
        // answer for every later one. Zero means "always stale", i.e. rebuilt per request.
        "services.projection.player-mapping-ttl-ms=0"
})
class ProjectionModelControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private ProjectionServiceClient projectionServiceClient;
    @MockitoBean private PlayerServiceClient playerServiceClient;
    /** Keeps the ESPN stat-line enrichment off the network; unstubbed means "no ESPN stats". */
    @MockitoBean private EspnServiceClient espnServiceClient;

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
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(mcdavid));
        when(playerServiceClient.getSkaters())
                .thenReturn(List.of(new SkaterResponse(5000, "Connor McDavid", "EDM", null, 97, Set.of(), null)));
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
        split.setShots(65);
        split.setPpGoals(4);
        split.setPpPoints(11);
        split.setShGoals(1);
        split.setShPoints(2);
        split.setHits(18);
        split.setBlocks(9);
        split.setFaceoffsWon(120);
        split.setFaceoffsLost(100);
        split.setToiSeconds(26000);
        split.setFirstTeamGame(63);
        split.setLastTeamGame(82);
        when(projectionServiceClient.skaterSplits(anyInt(), any(GameRange.class), anyInt()))
                .thenReturn(List.of(split));
        when(projectionServiceClient.goalieSplits(anyInt(), any(GameRange.class), anyInt()))
                .thenReturn(List.of());
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
                .andExpect(jsonPath("$.modelVersion").value("marcel-v3"));
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

    @Test
    @DisplayName("forwards an explicit from/to range to the projection service")
    void forwardsExplicitRange() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?season=2025&fromGame=50&toGame=82")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(projectionServiceClient).skaterSplits(2025, new GameRange(50, 82, null), 100);
    }

    @Test
    @DisplayName("an omitted range covers the whole season")
    void omittedRangeCoversTheSeason() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?season=2025")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(projectionServiceClient).skaterSplits(2025, GameRange.season(), 100);
    }

    @Test
    @DisplayName("rejects a range that says two different things")
    void rejectsConflictingRange() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?lastGames=20&fromGame=50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rejects a range that runs backwards")
    void rejectsInvertedRange() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=60&toGame=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("carries hits, blocks and faceoffs, and derives the splits that follow from them")
    void derivesStats() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?lastGames=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stats.hits").value(18.0))
                .andExpect(jsonPath("$[0].stats.blocks").value(9.0))
                .andExpect(jsonPath("$[0].stats.fw").value(120.0))
                .andExpect(jsonPath("$[0].stats.fl").value(100.0))
                .andExpect(jsonPath("$[0].stats.ppa").value(7.0)
                        ) // 11 power-play points less 4 power-play goals
                .andExpect(jsonPath("$[0].stats.sha").value(1.0))
                .andExpect(jsonPath("$[0].stats.shPct").value(20.0)) // 13 of 65
                .andExpect(jsonPath("$[0].stats.toiPerGame").value(1300.0)); // 26000s over 20
    }

    @Test
    @DisplayName("leaves out a rate there is nothing to divide by")
    void omitsUndefinedRates() throws Exception {
        SkaterSplitResponse noShots = new SkaterSplitResponse();
        noShots.setNhlId(8478402);
        noShots.setSeason(2025);
        noShots.setGames(0);
        noShots.setGoals(0);
        noShots.setShots(0);
        when(projectionServiceClient.skaterSplits(anyInt(), any(GameRange.class), anyInt()))
                .thenReturn(List.of(noShots));

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?lastGames=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stats.shPct").doesNotExist())
                .andExpect(jsonPath("$[0].stats.toiPerGame").doesNotExist());
    }
}
