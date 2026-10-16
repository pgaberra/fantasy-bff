package com.fantasy.bff.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.PlayerServiceClient.RosteredPlayer;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.RangeGoalieResponse;
import com.fantasy.bff.generated.projection.model.RangeNight;
import com.fantasy.bff.generated.projection.model.RangeProjectionsResponse;
import com.fantasy.bff.generated.projection.model.RangeSkaterResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The admins' demo of the free-agent list: no league, the Yahoo pool under a percent rostered
 * stands in for one, joined to the model on identity exactly as a league's wire is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // The mapping is cached in a singleton; zero means "always stale", so each test's stubs win.
        "services.projection.player-mapping-ttl-ms=0",
        "players.league-pool-ttl-ms=0"
})
class StreamerPlannerPoolFreeAgentsTest extends BaseIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);
    private static final String PATH = "/api/v1/admin/streamer-planner/free-agents";
    private static final String WEEK = "start=2026-10-12&end=2026-10-18";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private ProjectionServiceClient projectionServiceClient;
    @MockitoBean private YahooServiceClient yahooServiceClient;
    @MockitoBean private EspnServiceClient espnServiceClient;
    @MockitoBean private PlayerServiceClient playerServiceClient;

    private String admin;

    @BeforeEach
    void setUp() {
        admin = "Bearer " + jwtTokenValidator.generateToken("admin-1", "admin@example.com", true);
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(
                identity(8478402, "Connor McDavid", "EDM", 97),
                identity(8477970, "Spencer Knight", "CHI", 30),
                identity(8482821, "Arvid Soderblom", "CHI", 40)));
        when(projectionServiceClient.retiredPlayers()).thenReturn(List.of());
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        when(playerServiceClient.getRosteredPlayers()).thenReturn(List.of(
                new RosteredPlayer("3737", "Connor McDavid", "Edm", List.of("C"), false, 97, 99.0),
                new RosteredPlayer("5555", "Spencer Knight", "Chi", List.of("G"), true, 30, 12.0),
                new RosteredPlayer("7777", "Arvid Soderblom", "Chi", List.of("G"), true, 40, null)));
        when(projectionServiceClient.rangeProjections(eq(MONDAY), eq(SUNDAY), anyInt()))
                .thenReturn(projections());
    }

    @Test
    void listsOnlyThePoolUnderTheThresholdWithItsPercentRostered() throws Exception {
        mockMvc.perform(get(PATH + "?" + WEEK + "&maxPercentRostered=50").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.players[0].playerId").value("5555"))
                .andExpect(jsonPath("$.players[0].type").value("goalie"))
                .andExpect(jsonPath("$.players[0].percentRostered").value(12.0))
                .andExpect(jsonPath("$.players[0].availability").value("UNKNOWN"))
                .andExpect(jsonPath("$.players[0].stats.w").value(1.1));
    }

    @Test
    void takesInEveryoneUpToTheThresholdItself() throws Exception {
        mockMvc.perform(get(PATH + "?" + WEEK + "&maxPercentRostered=99").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(2))
                .andExpect(jsonPath("$.players[0].playerId").value("3737"))
                .andExpect(jsonPath("$.players[0].percentRostered").value(99.0));
    }

    /** Unknown is not low: a player Yahoo gives no percentage for is left out at any threshold. */
    @Test
    void leavesOutAPlayerWithNoPercentRostered() throws Exception {
        mockMvc.perform(get(PATH + "?" + WEEK + "&maxPercentRostered=100").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[?(@.playerId == '7777')]").isEmpty())
                .andExpect(jsonPath("$.creases[0].team").value("CHI"))
                .andExpect(jsonPath("$.creases[0].goalies[0].playerId").value("5555"))
                .andExpect(jsonPath("$.creases[0].goalies[1].playerId").doesNotExist());
    }

    @Test
    void isRefusedToAUserWhoIsNotAnAdmin() throws Exception {
        String user = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");

        mockMvc.perform(get(PATH + "?" + WEEK + "&maxPercentRostered=50").header("Authorization", user))
                .andExpect(status().isForbidden());

        verifyNoInteractions(playerServiceClient);
    }

    @Test
    void rejectsAThresholdOutsideZeroToAHundred() throws Exception {
        mockMvc.perform(get(PATH + "?" + WEEK + "&maxPercentRostered=101").header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readsNoLeague() throws Exception {
        mockMvc.perform(get(PATH + "?" + WEEK + "&maxPercentRostered=50").header("Authorization", admin))
                .andExpect(status().isOk());

        verifyNoInteractions(yahooServiceClient, espnServiceClient);
    }

    private static PlayerResponse identity(int nhlId, String name, String team, int sweater) {
        PlayerResponse player = new PlayerResponse();
        player.setNhlId(nhlId);
        player.setFullName(name);
        player.setCurrentTeam(team);
        player.setSweaterNumber(sweater);
        player.setIsActive(true);
        return player;
    }

    private static RangeProjectionsResponse projections() {
        RangeSkaterResponse mcdavid = new RangeSkaterResponse();
        mcdavid.setNhlId(8478402);
        mcdavid.setTeam("EDM");
        mcdavid.setClubGames(4);
        mcdavid.setExpectedGames(new BigDecimal("3.8"));
        mcdavid.setPoints(new BigDecimal("6.1"));
        mcdavid.setGoals(new BigDecimal("2.4"));

        RangeProjectionsResponse answer = new RangeProjectionsResponse();
        answer.setSeason(2026);
        answer.setModelVersion("marcel-v16");
        answer.setStart(MONDAY);
        answer.setEnd(SUNDAY);
        answer.setSkaters(List.of(mcdavid));
        answer.setGoalies(List.of(goalie(8477970, "2.1", "0.7"), goalie(8482821, "0.9", "0.3")));
        return answer;
    }

    private static RangeGoalieResponse goalie(int nhlId, String expectedGames, String share) {
        RangeGoalieResponse goalie = new RangeGoalieResponse();
        goalie.setNhlId(nhlId);
        goalie.setTeam("CHI");
        goalie.setClubGames(3);
        goalie.setExpectedGames(new BigDecimal(expectedGames));
        goalie.setWins(new BigDecimal("1.1"));
        goalie.setSaves(new BigDecimal("57.0"));
        goalie.setSavePct(new BigDecimal("0.908"));
        RangeNight night = new RangeNight();
        night.setGameDate(MONDAY);
        night.setShare(new BigDecimal(share));
        goalie.setNights(List.of(night));
        return goalie;
    }
}
