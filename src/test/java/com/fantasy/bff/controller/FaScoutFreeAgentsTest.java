package com.fantasy.bff.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import com.fantasy.bff.generated.projection.model.GoalieProjectionResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonGoalieResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonSkaterResponse;
import com.fantasy.bff.generated.projection.model.ServedRestOfSeasonGoalie;
import com.fantasy.bff.generated.projection.model.ServedRestOfSeasonSkater;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.generated.yahoo.model.YahooAvailablePlayerResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import java.math.BigDecimal;
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
 * The FA scout's one endpoint. A league's wire is joined to the model on identity, as in the
 * planner, and each available player goes out with two lines: the served rest of the season and
 * the frozen preseason one. Nothing is ranked here; these tests pin what each line carries.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "fa-scout.enabled=true",
        // The mapping is cached in a singleton; zero means "always stale", so each test's stubs win.
        "services.projection.player-mapping-ttl-ms=0"
})
class FaScoutFreeAgentsTest extends BaseIntegrationTest {

    private static final String YAHOO_LEAGUE = "/api/v1/fa-scout/free-agents?platform=YAHOO&leagueId=465.l.9";

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
                identity(8476875, "Brandon Montour", "SEA", 62),
                identity(8480069, "Cale Makar", "COL", 8),
                identity(8481611, "Logan Stankoven", "CAR", 11),
                identity(8477970, "Spencer Knight", "CHI", 30)));
        when(projectionServiceClient.retiredPlayers()).thenReturn(List.of());
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        when(projectionServiceClient.restOfSeasonSkaters(2026)).thenReturn(List.of(
                restOfSeason(8476875, "70", "1380", "48.5", "19.0"),
                restOfSeason(8481611, "72", "1010", "38.0", "6.0"),
                // Projected, but rostered in the league: not on the wire, so not listed.
                restOfSeason(8480069, "74", "1500", "90.0", "35.0")));
        when(projectionServiceClient.restOfSeasonGoalies(2026)).thenReturn(List.of(knightRestOfSeason()));
        when(projectionServiceClient.skaterProjections(2026, "preseason")).thenReturn(List.of(
                preseason(8476875, "80", "1050", "30.0", "4.0")));
        when(projectionServiceClient.goalieProjections(2026, "preseason")).thenReturn(List.of());
        when(yahooServiceClient.leagueFreeAgents(eq("user-1"), eq("465.l.9"), anyString(), anyInt()))
                .thenReturn(List.of(
                        yahooPlayer("1001", "Brandon Montour", "Sea", "D", false),
                        yahooPlayer("1002", "Logan Stankoven", "Car", "RW", false),
                        yahooPlayer("1003", "Spencer Knight", "Chi", "G", true)));
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

    private static RestOfSeasonSkaterResponse restOfSeason(
            int nhlId, String games, String toi, String points, String ppPoints) {
        RestOfSeasonSkaterResponse row = new RestOfSeasonSkaterResponse();
        row.setNhlId(nhlId);
        row.setTargetSeason(2026);
        row.setModelVersion("marcel-v118");
        row.setTeam("SEA");
        row.setGamesRemaining(78);
        // The bare expectation, which the scout must not read while a served line is there.
        row.setGamesPlayed(new BigDecimal("1"));
        row.setPoints(new BigDecimal("1"));
        ServedRestOfSeasonSkater served = new ServedRestOfSeasonSkater();
        served.setGamesPlayed(new BigDecimal(games));
        served.setToiPerGameSeconds(new BigDecimal(toi));
        served.setPoints(new BigDecimal(points));
        served.setGoals(new BigDecimal("12.0"));
        served.setPpPoints(new BigDecimal(ppPoints));
        served.setShPoints(new BigDecimal("0.5"));
        served.setShootingPct(new BigDecimal("0.08"));
        row.setServed(served);
        return row;
    }

    private static SkaterProjectionResponse preseason(
            int nhlId, String games, String toi, String points, String ppPoints) {
        return new SkaterProjectionResponse()
                .nhlId(nhlId)
                .targetSeason(2026)
                .modelVersion("preseason")
                .gamesPlayed(new BigDecimal(games))
                .toiPerGameSeconds(new BigDecimal(toi))
                .points(new BigDecimal(points))
                .ppPoints(new BigDecimal(ppPoints));
    }

    private static RestOfSeasonGoalieResponse knightRestOfSeason() {
        RestOfSeasonGoalieResponse row = new RestOfSeasonGoalieResponse();
        row.setNhlId(8477970);
        row.setTargetSeason(2026);
        row.setModelVersion("marcel-v118");
        row.setTeam("CHI");
        row.setGamesRemaining(78);
        ServedRestOfSeasonGoalie served = new ServedRestOfSeasonGoalie();
        served.setGamesPlayed(new BigDecimal("52"));
        served.setGamesStarted(new BigDecimal("50"));
        served.setWins(new BigDecimal("24"));
        served.setLosses(new BigDecimal("20"));
        served.setOtLosses(new BigDecimal("6"));
        served.setSavePct(new BigDecimal("0.905"));
        row.setServed(served);
        return row;
    }

    private static YahooAvailablePlayerResponse yahooPlayer(
            String id, String name, String team, String position, boolean goalie) {
        YahooAvailablePlayerResponse player = new YahooAvailablePlayerResponse();
        player.setYahooId(id);
        player.setFullName(name);
        player.setTeamAbbrev(team);
        player.setPosition(position);
        player.setGoalie(goalie);
        player.setEligiblePositions(List.of(position));
        player.setAvailability(YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT);
        return player;
    }

    @Test
    void theFeatureIsReportedOn() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faScout").value(true));
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE)).andExpect(status().isUnauthorized());
    }

    @Test
    void joinsTheWireToTheServedRestOfTheSeasonAndThePreseasonLine() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value(2026))
                .andExpect(jsonPath("$.modelVersion").value("marcel-v118"))
                .andExpect(jsonPath("$.inSeason").value(true))
                .andExpect(jsonPath("$.preseasonAvailable").value(true))
                // Makar is projected but rostered, so only the three on the wire are listed.
                .andExpect(jsonPath("$.players", hasSize(3)))
                // The default order is games ahead, most first: Stankoven, Montour, then Knight.
                .andExpect(jsonPath("$.players[1].playerId").value("1001"))
                .andExpect(jsonPath("$.players[1].name").value("Brandon Montour"))
                .andExpect(jsonPath("$.players[1].type").value("skater"))
                .andExpect(jsonPath("$.players[1].availability").value("FREE_AGENT"))
                .andExpect(jsonPath("$.players[1].restOfSeason.games").value(70.0))
                .andExpect(jsonPath("$.players[1].restOfSeason.stats.points").value(48.5))
                .andExpect(jsonPath("$.players[1].restOfSeason.stats.ppp").value(19.0))
                .andExpect(jsonPath("$.players[1].restOfSeason.stats.toiPerGame").value(1380.0))
                .andExpect(jsonPath("$.players[1].restOfSeason.stats.toi").value(96600.0))
                .andExpect(jsonPath("$.players[1].restOfSeason.stats.shPct").value(8.0))
                .andExpect(jsonPath("$.players[1].restOfSeason.stats.stp").value(19.5))
                // A defenceman's points count as defencemen's points too.
                .andExpect(jsonPath("$.players[1].restOfSeason.stats.defPoints").value(48.5))
                .andExpect(jsonPath("$.players[1].preseason.games").value(80.0))
                .andExpect(jsonPath("$.players[1].preseason.stats.points").value(30.0))
                .andExpect(jsonPath("$.players[1].preseason.stats.toiPerGame").value(1050.0))
                // No preseason line for him: absent, not zero.
                .andExpect(jsonPath("$.players[0].name").value("Logan Stankoven"))
                .andExpect(jsonPath("$.players[0].restOfSeason.stats.defPoints").doesNotExist())
                .andExpect(jsonPath("$.players[0].preseason").value(nullValue()))
                .andExpect(jsonPath("$.players[2].name").value("Spencer Knight"))
                .andExpect(jsonPath("$.players[2].type").value("goalie"))
                .andExpect(jsonPath("$.players[2].restOfSeason.games").value(50.0))
                .andExpect(jsonPath("$.players[2].restOfSeason.stats.gs").value(50.0))
                .andExpect(jsonPath("$.players[2].restOfSeason.stats.w").value(24.0))
                .andExpect(jsonPath("$.players[2].restOfSeason.stats.winPct").value(0.48))
                .andExpect(jsonPath("$.players[2].restOfSeason.stats.svPct").value(0.905));
    }

    @Test
    void readsAnEspnLeaguesWireToo() throws Exception {
        AvailablePlayer montour = new AvailablePlayer();
        montour.setEspnId(3900L);
        montour.setFullName("Brandon Montour");
        montour.setTeamAbbrev("Sea");
        montour.setGoalie(false);
        montour.setEligiblePositions(List.of("D"));
        montour.setAvailability(AvailablePlayer.AvailabilityEnum.WAIVERS);
        when(espnServiceClient.leagueFreeAgents(eq("user-1"), eq("12345"), anyString(), anyInt()))
                .thenReturn(List.of(montour));

        mockMvc.perform(get("/api/v1/fa-scout/free-agents?platform=ESPN&leagueId=12345")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players", hasSize(1)))
                .andExpect(jsonPath("$.players[0].playerId").value("3900"))
                .andExpect(jsonPath("$.players[0].availability").value("WAIVERS"))
                .andExpect(jsonPath("$.players[0].preseason.stats.points").value(30.0));
    }

    @Test
    void withoutAPreseasonSnapshotNobodyHasAPreseasonLine() throws Exception {
        when(projectionServiceClient.skaterProjections(2026, "preseason")).thenReturn(List.of());

        mockMvc.perform(get(YAHOO_LEAGUE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preseasonAvailable").value(false))
                .andExpect(jsonPath("$.players[1].preseason").value(nullValue()));
    }

    @Test
    void outOfSeasonListsNobodyAndDoesNotAskThePlatform() throws Exception {
        when(projectionServiceClient.restOfSeasonSkaters(2026)).thenReturn(List.of());
        when(projectionServiceClient.restOfSeasonGoalies(2026)).thenReturn(List.of());

        mockMvc.perform(get(YAHOO_LEAGUE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inSeason").value(false))
                .andExpect(jsonPath("$.players", hasSize(0)));
        verifyNoInteractions(yahooServiceClient);
    }

    @Test
    void anOversizedLeagueIdIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/fa-scout/free-agents?platform=YAHOO&leagueId=" + "x".repeat(65))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(yahooServiceClient);
    }
}
