package com.fantasy.bff.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.RookiesResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.SharedPlayer;
import com.fantasy.bff.generated.db.model.SharedProjectionData;
import com.fantasy.bff.generated.db.model.SharedProjectionResponse;
import com.fantasy.bff.service.RookieService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Players' pictures are the platform's photographs, and we hold no licence to show them. So the
 * switch is off without anyone setting it, and off has to mean no picture reaches a browser by any
 * door: not in the player lists, not from the headshot endpoint, and not from a share snapshot
 * that stored an address before the switch.
 *
 * <p>Nothing is set here on purpose. The configuration under test is the default one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerAvatarsDisabledTest extends BaseIntegrationTest {

    private static final String SHARE_TOKEN = "s0mErAnd0mT0k3nV4lu3ab";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PlayerServiceClient playerServiceClient;
    /** Keeps the ESPN stat-line enrichment off the network; unstubbed means "no ESPN stats". */
    @MockitoBean private EspnServiceClient espnServiceClient;
    @MockitoBean private DatabaseServiceClient databaseServiceClient;
    @MockitoBean private RookieService rookieService;

    @BeforeEach
    void rookieStatusIsUnknown() {
        when(rookieService.rookies()).thenReturn(RookiesResponse.unknown());
    }

    @Test
    @DisplayName("a skater goes out with no headshot, even when the pool has one")
    void skaters_carryNoHeadshot() throws Exception {
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of(
                new SkaterResponse(1, "Connor McDavid", "EDM", "/players/1/headshot?v=96-1.55-0.04",
                        97, Set.of(SkaterPosition.C),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(82, 1320),
                                new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0,
                                        1, 23, 38, 61, 8, 2, 348, 18.4, 812, 623, 42, 28, 0, 1408,
                                        92400)))));

        mockMvc.perform(get("/api/v1/players/skaters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Connor McDavid"))
                .andExpect(jsonPath("$[0].headshot").doesNotExist());
    }

    @Test
    @DisplayName("and so does a goalie")
    void goalies_carryNoHeadshot() throws Exception {
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of(
                new GoalieResponse(101, "Igor Shesterkin", "NYR", "/players/101/headshot?v=96-1.55-0.04",
                        31,
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(58),
                                new GoalieResponse.ScoringStats(58, 36, 17, 4, 3, 1720, 1565, 155,
                                        2.67, 0.910, 0.632, 209000)))));

        mockMvc.perform(get("/api/v1/players/goalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Igor Shesterkin"))
                .andExpect(jsonPath("$[0].headshot").doesNotExist());
    }

    /**
     * A browser can still hold an address from before the switch, and a share snapshot stores
     * one, so the endpoint itself has to refuse — and without fetching the photograph first.
     */
    @Test
    @DisplayName("the headshot endpoint serves nothing and never asks the pool for a picture")
    void headshot_isNotFoundAndNeverFetched() throws Exception {
        when(playerServiceClient.getHeadshot(1)).thenReturn(Optional.of(new byte[] {1, 2, 3}));

        mockMvc.perform(get("/api/v1/players/1/headshot"))
                .andExpect(status().isNotFound());

        verify(playerServiceClient, never()).getHeadshot(anyInt());
    }

    @Test
    @DisplayName("a share published with pictures is read back without them")
    void sharedBoard_carriesNoHeadshot() throws Exception {
        when(databaseServiceClient.getSharedProjection(SHARE_TOKEN)).thenReturn(sharedBoardWithAPicture());

        mockMvc.perform(get("/api/v1/shared/" + SHARE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players[0].name").value("Connor McDavid"))
                .andExpect(jsonPath("$.data.players[0].headshot").doesNotExist());
    }

    private static SharedProjectionResponse sharedBoardWithAPicture() {
        SharedPlayer mcDavid = new SharedPlayer()
                .playerId(1)
                .name("Connor McDavid")
                .teamAbbrev("EDM")
                .headshot("/players/1/headshot?v=96-1.55-0.04")
                .positions(List.of("C"))
                .type(SharedPlayer.TypeEnum.SKATER)
                .rank(1)
                .value(412.5)
                .stats(new PlayerStats()
                        .utility(Map.of("gp", 82.0))
                        .scoring(Map.of("goals", 64.0)));
        return new SharedProjectionResponse()
                .token(SHARE_TOKEN)
                .name("My league")
                .authorUsername("Alex")
                .season(SharedProjectionResponse.SeasonEnum._20262027)
                .data(new SharedProjectionData()
                        .projectionSettings(new ProjectionSettings()
                                .scoringType(ProjectionSettings.ScoringTypeEnum.POINTS)
                                .statWeights(Map.of("goals", 4.5))
                                .activeScoringColumns(List.of("goals"))
                                .activeUtilityColumns(List.of("gp"))
                                .scaleSettings(Map.of())
                                .decimalSettings(Map.of("goals", 0))
                                .useDefaultDecimals(true)
                                .leagueSize(12))
                        .players(List.of(mcDavid)))
                .createdAt(OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.of(2026, 8, 2, 10, 0, 0, 0, ZoneOffset.UTC));
    }
}
