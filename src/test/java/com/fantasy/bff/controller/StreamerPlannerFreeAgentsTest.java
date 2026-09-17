package com.fantasy.bff.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.generated.espn.model.AvailablePlayer;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.RangeGoalieResponse;
import com.fantasy.bff.generated.projection.model.RangeProjectionsResponse;
import com.fantasy.bff.generated.projection.model.RangeSkaterResponse;
import com.fantasy.bff.generated.yahoo.model.YahooAvailablePlayerResponse;
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
 * The free-agent half of the planner. The platform's ids and the model's NHL ids are joined on
 * identity, so these tests pin the join rather than a lookup table: McDavid is the same man on
 * both sides, and a free agent the model does not project is left out rather than zeroed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "streamer-planner.enabled=true",
        // The mapping is cached in a singleton; zero means "always stale", so each test's stubs win.
        "services.projection.player-mapping-ttl-ms=0"
})
class StreamerPlannerFreeAgentsTest extends BaseIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);
    private static final String WEEK = "start=2026-10-12&end=2026-10-18";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private ProjectionServiceClient projectionServiceClient;
    @MockitoBean private YahooServiceClient yahooServiceClient;
    @MockitoBean private EspnServiceClient espnServiceClient;
    @MockitoBean private PlayerServiceClient playerServiceClient;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(
                identity(8478402, "Connor McDavid", "EDM", 97),
                identity(8477970, "Spencer Knight", "CHI", 30)));
        when(projectionServiceClient.retiredPlayers()).thenReturn(List.of());
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        when(projectionServiceClient.rangeProjections(eq(MONDAY), eq(SUNDAY), anyInt()))
                .thenReturn(projections());
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
        mcdavid.setPpPoints(new BigDecimal("2.0"));
        mcdavid.setShPoints(new BigDecimal("0.2"));
        mcdavid.setToiPerGameSeconds(new BigDecimal("1310"));

        RangeGoalieResponse knight = new RangeGoalieResponse();
        knight.setNhlId(8477970);
        knight.setTeam("CHI");
        knight.setClubGames(3);
        knight.setExpectedGames(new BigDecimal("2.1"));
        knight.setWins(new BigDecimal("1.1"));
        knight.setSaves(new BigDecimal("57.0"));
        knight.setSavePct(new BigDecimal("0.908"));

        RangeProjectionsResponse answer = new RangeProjectionsResponse();
        answer.setSeason(2026);
        answer.setModelVersion("marcel-v16");
        answer.setStart(MONDAY);
        answer.setEnd(SUNDAY);
        answer.setSkaters(List.of(mcdavid));
        answer.setGoalies(List.of(knight));
        return answer;
    }

    private static YahooAvailablePlayerResponse yahooPlayer(
            String id, String name, String team, String position, boolean goalie,
            YahooAvailablePlayerResponse.AvailabilityEnum availability) {
        YahooAvailablePlayerResponse player = new YahooAvailablePlayerResponse();
        player.setYahooId(id);
        player.setFullName(name);
        player.setTeamAbbrev(team);
        player.setPosition(position);
        player.setGoalie(goalie);
        player.setEligiblePositions(List.of(position));
        player.setAvailability(availability);
        return player;
    }

    @Test
    void joinsTheYahooWireToTheModelsLineForTheWeek() throws Exception {
        when(yahooServiceClient.leagueFreeAgents(eq("user-1"), eq("465.l.9"), anyInt()))
                .thenReturn(List.of(
                        yahooPlayer("3737", "Connor McDavid", "Edm", "C", false,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT),
                        yahooPlayer("5555", "Spencer Knight", "Chi", "G", true,
                                YahooAvailablePlayerResponse.AvailabilityEnum.WAIVERS),
                        yahooPlayer("9999", "Nobody Projected", "SJS", "LW", false,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT)));

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value(2026))
                .andExpect(jsonPath("$.modelVersion").value("marcel-v16"))
                .andExpect(jsonPath("$.players.length()").value(2))
                // Most expected games first, which is the order a streamer reads.
                .andExpect(jsonPath("$.players[0].playerId").value("3737"))
                .andExpect(jsonPath("$.players[0].type").value("skater"))
                .andExpect(jsonPath("$.players[0].availability").value("FREE_AGENT"))
                .andExpect(jsonPath("$.players[0].clubGames").value(4))
                .andExpect(jsonPath("$.players[0].expectedGames").value(3.8))
                .andExpect(jsonPath("$.players[0].stats.points").value(6.1))
                // Special teams are summed for a league that scores them as one category.
                .andExpect(jsonPath("$.players[0].stats.stp").value(2.2))
                .andExpect(jsonPath("$.players[1].playerId").value("5555"))
                .andExpect(jsonPath("$.players[1].type").value("goalie"))
                .andExpect(jsonPath("$.players[1].availability").value("WAIVERS"))
                .andExpect(jsonPath("$.players[1].stats.svPct").value(0.908))
                // The unprojected free agent is counted, not shown with zeroes.
                .andExpect(jsonPath("$.unprojected").value(1));
    }

    @Test
    void readsAnEspnLeagueTheSameWay() throws Exception {
        AvailablePlayer mcdavid = new AvailablePlayer();
        mcdavid.setEspnId(3895074L);
        mcdavid.setFullName("Connor McDavid");
        mcdavid.setTeamAbbrev("EDM");
        mcdavid.setPosition("C");
        mcdavid.setGoalie(false);
        mcdavid.setEligiblePositions(List.of("C"));
        mcdavid.setAvailability(AvailablePlayer.AvailabilityEnum.FREE_AGENT);
        when(espnServiceClient.leagueFreeAgents(eq("user-1"), eq("12345"), anyInt()))
                .thenReturn(List.of(mcdavid));

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=ESPN&leagueId=12345&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].playerId").value("3895074"))
                .andExpect(jsonPath("$.players[0].name").value("Connor McDavid"))
                .andExpect(jsonPath("$.players[0].positions[0]").value("C"));
        verifyNoInteractions(yahooServiceClient);
    }

    @Test
    void anOverlongStretchIsRefusedBeforeAnyPlatformIsAsked() throws Exception {
        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9"
                        + "&start=2026-10-01&end=2026-11-05").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(yahooServiceClient);
        verifyNoInteractions(espnServiceClient);
    }

    @Test
    void anUnknownPlatformIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=SLEEPER&leagueId=1&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=1&" + WEEK))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(yahooServiceClient);
    }

    @Test
    void aLeagueWithNothingAvailableIsAnEmptyList() throws Exception {
        when(yahooServiceClient.leagueFreeAgents(anyString(), anyString(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(0))
                .andExpect(jsonPath("$.unprojected").value(0));
    }
}
