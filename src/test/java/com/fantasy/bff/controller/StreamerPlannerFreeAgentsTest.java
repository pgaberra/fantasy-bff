package com.fantasy.bff.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.exception.YahooAccessDeniedException;
import com.fantasy.bff.generated.espn.model.AvailablePlayer;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.RangeGoalieResponse;
import com.fantasy.bff.generated.projection.model.RangeProjectionsResponse;
import com.fantasy.bff.generated.projection.model.RangeSkaterResponse;
import com.fantasy.bff.generated.yahoo.model.YahooAvailablePlayerResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import com.fantasy.bff.service.scoring.ScoringStatKeys;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The free-agent half of the planner. The platform's ids and the model's NHL ids are joined on
 * identity, so these tests pin the join rather than a lookup table: McDavid is the same man on
 * both sides, and a free agent the model does not project is left out rather than zeroed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
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
                identity(8480069, "Cale Makar", "COL", 8),
                identity(8477970, "Spencer Knight", "CHI", 30),
                identity(8482821, "Arvid Soderblom", "CHI", 40)));
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

        return projections(List.of(mcdavid), List.of(knight));
    }

    private static RangeProjectionsResponse projections(
            List<RangeSkaterResponse> skaters, List<RangeGoalieResponse> goalies) {
        RangeProjectionsResponse answer = new RangeProjectionsResponse();
        answer.setSeason(2026);
        answer.setModelVersion("marcel-v16");
        answer.setStart(MONDAY);
        answer.setEnd(SUNDAY);
        answer.setSkaters(skaters);
        answer.setGoalies(goalies);
        return answer;
    }

    private static RangeSkaterResponse fullSkaterLine(int nhlId, String team, String expectedGames) {
        RangeSkaterResponse line = new RangeSkaterResponse();
        line.setNhlId(nhlId);
        line.setTeam(team);
        line.setClubGames(4);
        line.setExpectedGames(new BigDecimal(expectedGames));
        line.setGoals(new BigDecimal("2.4"));
        line.setAssists(new BigDecimal("3.7"));
        line.setPoints(new BigDecimal("6.1"));
        line.setPlusMinus(new BigDecimal("1.2"));
        line.setPim(new BigDecimal("1.5"));
        line.setPpGoals(new BigDecimal("0.8"));
        line.setPpAssists(new BigDecimal("1.2"));
        line.setPpPoints(new BigDecimal("2.0"));
        line.setShGoals(new BigDecimal("0.1"));
        line.setShAssists(new BigDecimal("0.1"));
        line.setShPoints(new BigDecimal("0.2"));
        line.setGwGoals(new BigDecimal("0.4"));
        line.setShots(new BigDecimal("19.2"));
        line.setFaceoffsWon(new BigDecimal("30.0"));
        line.setFaceoffsLost(new BigDecimal("28.0"));
        line.setHits(new BigDecimal("3.0"));
        line.setBlocks(new BigDecimal("2.0"));
        line.setShifts(new BigDecimal("88.0"));
        line.setHatTricks(new BigDecimal("0.05"));
        line.setToiPerGameSeconds(new BigDecimal("1310"));
        line.setShootingPct(new BigDecimal("0.125"));
        return line;
    }

    private static RangeGoalieResponse fullGoalieLine(
            int nhlId, String expectedGames, String wins, String losses, String otLosses) {
        RangeGoalieResponse line = new RangeGoalieResponse();
        line.setNhlId(nhlId);
        line.setTeam("CHI");
        line.setClubGames(3);
        line.setExpectedGames(new BigDecimal(expectedGames));
        line.setWins(new BigDecimal(wins));
        line.setLosses(new BigDecimal(losses));
        line.setOtLosses(new BigDecimal(otLosses));
        line.setShutouts(new BigDecimal("0.1"));
        line.setShotsAgainst(new BigDecimal("62.0"));
        line.setSaves(new BigDecimal("57.0"));
        line.setGoalsAgainst(new BigDecimal("5.0"));
        line.setToiSeconds(new BigDecimal("7560"));
        line.setGoalsAgainstAvg(new BigDecimal("2.61"));
        line.setSavePct(new BigDecimal("0.908"));
        return line;
    }

    private ResultActions fullLinesForTheYahooWire() throws Exception {
        YahooAvailablePlayerResponse makar = yahooPlayer("6789", "Cale Makar", "Col", "D", false,
                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT);
        makar.setEligiblePositions(List.of("D", "Util"));
        when(yahooServiceClient.leagueFreeAgents(eq("user-1"), eq("465.l.9"), anyString(), anyInt()))
                .thenReturn(List.of(
                        makar,
                        yahooPlayer("3737", "Connor McDavid", "Edm", "C", false,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT),
                        yahooPlayer("5555", "Spencer Knight", "Chi", "G", true,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT),
                        yahooPlayer("7777", "Arvid Soderblom", "Chi", "G", true,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT)));
        when(projectionServiceClient.rangeProjections(eq(MONDAY), eq(SUNDAY), anyInt()))
                .thenReturn(projections(
                        List.of(
                                fullSkaterLine(8480069, "COL", "3.9"),
                                fullSkaterLine(8478402, "EDM", "3.8")),
                        List.of(
                                fullGoalieLine(8477970, "2.1", "1.1", "0.7", "0.2"),
                                fullGoalieLine(8482821, "0.0", "0.0", "0.0", "0.0"))));

        return mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].name").value("Cale Makar"))
                .andExpect(jsonPath("$.players[1].name").value("Connor McDavid"))
                .andExpect(jsonPath("$.players[2].name").value("Spencer Knight"))
                .andExpect(jsonPath("$.players[3].name").value("Arvid Soderblom"));
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
        when(yahooServiceClient.leagueFreeAgents(eq("user-1"), eq("465.l.9"), anyString(), anyInt()))
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
                // The unprojected free agent is left out, neither shown with zeroes nor counted.
                .andExpect(jsonPath("$.unprojected").doesNotExist());
    }

    @Test
    void logsWhyEachUnrankedFreeAgentWasLeftOut(CapturedOutput output) throws Exception {
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(
                identity(8478402, "Connor McDavid", "EDM", 97),
                identity(8477970, "Spencer Knight", "CHI", 30),
                identity(8485000, "Rostered Prospect", "SJS", 61)));
        when(yahooServiceClient.leagueFreeAgents(eq("user-1"), eq("465.l.9"), anyString(), anyInt()))
                .thenReturn(List.of(
                        yahooPlayer("3737", "Connor McDavid", "Edm", "C", false,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT),
                        // The platform calls him a skater; the model projects him in goal.
                        yahooPlayer("5555", "Spencer Knight", "Chi", "C", false,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT),
                        yahooPlayer("6161", "Rostered Prospect", "SJ", "LW", false,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT),
                        yahooPlayer("9999", "Nobody Projected", "SJS", "LW", false,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT)));

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1));

        assertThat(output).contains(
                "left 3 of 4 available YAHOO players unranked: "
                        + "1 matched no NHL player [Nobody Projected (9999)], "
                        + "1 have no projection [Rostered Prospect (6161)], "
                        + "1 are a skater on one side and a goalie on the other [Spencer Knight (5555)]");
    }

    @Test
    void shootingPercentageArrivesAsAPercentNotTheModelsFraction() throws Exception {
        fullLinesForTheYahooWire()
                .andExpect(jsonPath("$.players[1].stats.shPct").value(12.5));
    }

    @Test
    void aSkatersIceTimeIsHisPerGameFigureOverTheGamesHeIsExpectedToPlay() throws Exception {
        fullLinesForTheYahooWire()
                .andExpect(jsonPath("$.players[1].stats.toiPerGame").value(1310.0))
                .andExpect(jsonPath("$.players[1].stats.toi").value(4978.0));
    }

    @Test
    void defencemenPointsAreADefencemansPointsAndNoneOfAForwards() throws Exception {
        fullLinesForTheYahooWire()
                .andExpect(jsonPath("$.players[0].positions[0]").value("D"))
                .andExpect(jsonPath("$.players[0].stats.defPoints").value(6.1))
                .andExpect(jsonPath("$.players[1].positions[0]").value("C"))
                .andExpect(jsonPath("$.players[1].stats.points").value(6.1))
                .andExpect(jsonPath("$.players[1].stats.defPoints").doesNotExist());
    }

    @Test
    void aGoaliesWinPercentageIsHisShareOfDecisionsAndAbsentWithoutAny() throws Exception {
        fullLinesForTheYahooWire()
                .andExpect(jsonPath("$.players[2].stats.winPct").value(0.55))
                .andExpect(jsonPath("$.players[3].stats.w").value(0.0))
                .andExpect(jsonPath("$.players[3].stats.winPct").doesNotExist());
    }

    @Test
    void aFullLineCarriesEveryStatALeagueCanScore() throws Exception {
        ResultActions answer = fullLinesForTheYahooWire();
        for (String key : ScoringStatKeys.SKATER_SCORING) {
            answer.andExpect(jsonPath("$.players[0].stats." + key).exists());
        }
        for (String key : ScoringStatKeys.GOALIE_SCORING) {
            answer.andExpect(jsonPath("$.players[2].stats." + key).exists());
        }
    }

    @Test
    void asksForEachPositionOnItsOwnAndListsAPlayerEligibleAtTwoOnce() throws Exception {
        YahooAvailablePlayerResponse mcdavid = yahooPlayer("3737", "Connor McDavid", "Edm", "C", false,
                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT);
        mcdavid.setEligiblePositions(List.of("C", "LW"));
        // A backup goalie sits far below any mixed top 300; asked by position he is there.
        YahooAvailablePlayerResponse soderblom = yahooPlayer("7777", "Arvid Soderblom", "Chi", "G", true,
                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT);
        when(yahooServiceClient.leagueFreeAgents(eq("user-1"), eq("465.l.9"), anyString(), anyInt()))
                .thenReturn(List.of());
        when(yahooServiceClient.leagueFreeAgents("user-1", "465.l.9", "G", 50))
                .thenReturn(List.of(soderblom));
        when(yahooServiceClient.leagueFreeAgents("user-1", "465.l.9", "C", 75))
                .thenReturn(List.of(mcdavid));
        when(yahooServiceClient.leagueFreeAgents("user-1", "465.l.9", "LW", 75))
                .thenReturn(List.of(mcdavid));
        when(yahooServiceClient.leagueFreeAgents("user-1", "465.l.9", "D", 75))
                .thenReturn(List.of(yahooPlayer("6789", "Cale Makar", "Col", "D", false,
                        YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT)));
        RangeGoalieResponse backup = new RangeGoalieResponse();
        backup.setNhlId(8482821);
        backup.setTeam("CHI");
        backup.setClubGames(3);
        backup.setExpectedGames(new BigDecimal("0.5"));
        when(projectionServiceClient.rangeProjections(eq(MONDAY), eq(SUNDAY), anyInt()))
                .thenReturn(projections(
                        List.of(fullSkaterLine(8478402, "EDM", "3.8"), fullSkaterLine(8480069, "COL", "3.9")),
                        List.of(backup)));

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(3))
                .andExpect(jsonPath("$.players[0].name").value("Cale Makar"))
                .andExpect(jsonPath("$.players[1].name").value("Connor McDavid"))
                .andExpect(jsonPath("$.players[1].positions[1]").value("LW"))
                .andExpect(jsonPath("$.players[2].name").value("Arvid Soderblom"))
                .andExpect(jsonPath("$.players[2].type").value("goalie"))
                .andExpect(jsonPath("$.players[2].expectedGames").value(0.5));

        for (String position : List.of("C", "LW", "RW", "D")) {
            verify(yahooServiceClient).leagueFreeAgents("user-1", "465.l.9", position, 75);
        }
        verify(yahooServiceClient).leagueFreeAgents("user-1", "465.l.9", "G", 50);
        verifyNoMoreInteractions(yahooServiceClient);
    }

    @Test
    void aPositionThatFailsFailsTheListWithItsOwnError() throws Exception {
        when(yahooServiceClient.leagueFreeAgents(eq("user-1"), eq("465.l.9"), anyString(), anyInt()))
                .thenReturn(List.of());
        when(yahooServiceClient.leagueFreeAgents("user-1", "465.l.9", "D", 75))
                .thenThrow(new YahooAccessDeniedException("Yahoo refused"));

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isFailedDependency());
    }

    @Test
    void asksAnEspnLeagueForEachPositionToo() throws Exception {
        when(espnServiceClient.leagueFreeAgents(eq("user-1"), eq("12345"), anyString(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=ESPN&leagueId=12345&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk());

        for (String position : List.of("C", "LW", "RW", "D")) {
            verify(espnServiceClient).leagueFreeAgents("user-1", "12345", position, 75);
        }
        verify(espnServiceClient).leagueFreeAgents("user-1", "12345", "G", 50);
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
        when(espnServiceClient.leagueFreeAgents(eq("user-1"), eq("12345"), anyString(), anyInt()))
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
        when(yahooServiceClient.leagueFreeAgents(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(0));
    }
}
