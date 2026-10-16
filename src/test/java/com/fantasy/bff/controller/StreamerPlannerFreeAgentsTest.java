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
import com.fantasy.bff.generated.espn.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.espn.model.LeagueRosterTeam;
import com.fantasy.bff.generated.espn.model.LeagueRostersResponse;
import com.fantasy.bff.generated.espn.model.SkaterResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.RangeGoalieResponse;
import com.fantasy.bff.generated.projection.model.RangeNight;
import com.fantasy.bff.generated.projection.model.RangeProjectionsResponse;
import com.fantasy.bff.generated.projection.model.RangeSkaterResponse;
import com.fantasy.bff.generated.yahoo.model.YahooAvailablePlayerResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import com.fantasy.bff.service.scoring.ScoringStatKeys;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
        // The mapping is cached in a singleton; zero means "always stale", so each test's stubs win.
        "services.projection.player-mapping-ttl-ms=0",
        "players.league-pool-ttl-ms=0"
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
        yahooWire(List.of(
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

    /**
     * The league's wire as these players: the pool holds them and one rostered star, the
     * league's one team holds the star, and the platform's waivers are those marked so.
     */
    private void yahooWire(List<YahooAvailablePlayerResponse> available) {
        List<PlayerServiceClient.RosteredPlayer> pool = new ArrayList<>();
        pool.add(new PlayerServiceClient.RosteredPlayer(
                "1", "Rostered Star", "Edm", List.of("C"), false, 29, 100.0));
        for (YahooAvailablePlayerResponse player : available) {
            pool.add(new PlayerServiceClient.RosteredPlayer(
                    player.getYahooId(), player.getFullName(), player.getTeamAbbrev(),
                    player.getEligiblePositions(), Boolean.TRUE.equals(player.getGoalie()),
                    player.getUniformNumber(), 1.0));
        }
        when(playerServiceClient.getRosteredPlayers()).thenReturn(pool);
        com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer star =
                new com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer();
        star.setPlayerId(1);
        star.setFullName("Rostered Star");
        star.setTeamAbbrev("Edm");
        star.setEligiblePositions(List.of("C"));
        com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam team =
                new com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam();
        team.setName("Rival");
        team.setMine(false);
        team.setPlayers(List.of(star));
        com.fantasy.bff.generated.yahoo.model.LeagueRostersResponse rosters =
                new com.fantasy.bff.generated.yahoo.model.LeagueRostersResponse();
        rosters.setLeagueKey("465.l.9");
        rosters.setTeams(List.of(team));
        when(yahooServiceClient.rosters("user-1", "465.l.9")).thenReturn(rosters);
        when(yahooServiceClient.leagueWaivers("user-1", "465.l.9", 300)).thenReturn(available.stream()
                .filter(player -> player.getAvailability() == YahooAvailablePlayerResponse.AvailabilityEnum.WAIVERS)
                .toList());
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
        yahooWire(List.of(
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
                identity(8485000, "Rostered Prospect", "SJS", 61),
                identity(8486000, "Ghost Skater", "UTA", 44)));
        // The model projects a man the pool has under no name it can match.
        RangeSkaterResponse ghost = fullSkaterLine(8486000, "UTA", "3.0");
        RangeProjectionsResponse withGhost = projections();
        withGhost.setSkaters(List.of(withGhost.getSkaters().getFirst(), ghost));
        when(projectionServiceClient.rangeProjections(eq(MONDAY), eq(SUNDAY), anyInt())).thenReturn(withGhost);
        yahooWire(List.of(
                        yahooPlayer("3737", "Connor McDavid", "Edm", "C", false,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT),
                        // The platform calls him a skater, the model projects him in goal.
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

        // The two big groups are mostly the pool's minor-leaguers, so they are counted, not named.
        assertThat(output).contains(
                "left 3 of 4 available YAHOO players unranked: "
                        + "1 matched no NHL player, "
                        + "1 have no projection, "
                        + "1 are a skater on one side and a goalie on the other [Spencer Knight (5555)]; "
                        + "1 projected players matched nobody in the pool [Ghost Skater (8486000)]");
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
    void listsEveryPoolPlayerNoTeamHoldsAndAsksThePlatformOnlyForItsWaivers() throws Exception {
        YahooAvailablePlayerResponse mcdavid = yahooPlayer("3737", "Connor McDavid", "Edm", "C", false,
                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT);
        mcdavid.setEligiblePositions(List.of("C", "LW"));
        // A backup goalie sits below any capped page of the platform's ranking; the pool has him.
        YahooAvailablePlayerResponse soderblom = yahooPlayer("7777", "Arvid Soderblom", "Chi", "G", true,
                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT);
        yahooWire(List.of(mcdavid, soderblom, yahooPlayer("6789", "Cale Makar", "Col", "D", false,
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

        verify(yahooServiceClient).rosters("user-1", "465.l.9");
        verify(yahooServiceClient).leagueWaivers("user-1", "465.l.9", 300);
        verifyNoMoreInteractions(yahooServiceClient);
    }

    private static RangeGoalieResponse creaseLine(int nhlId, String team, String... shares) {
        RangeGoalieResponse line = new RangeGoalieResponse();
        line.setNhlId(nhlId);
        line.setTeam(team);
        line.setClubGames(shares.length);
        BigDecimal starts = BigDecimal.ZERO;
        List<RangeNight> nights = new ArrayList<>();
        for (int index = 0; index < shares.length; index++) {
            RangeNight night = new RangeNight();
            night.setGameDate(MONDAY.plusDays(2L * index + 1));
            night.setShare(new BigDecimal(shares[index]));
            nights.add(night);
            starts = starts.add(new BigDecimal(shares[index]));
        }
        line.setExpectedGames(starts);
        line.setNights(nights);
        return line;
    }

    @Test
    void saysOnTheNightTheClubHasNamedItsStarterHowSureThePageIs() throws Exception {
        yahooWire(List.of(yahooPlayer("5555", "Spencer Knight", "Chi", "G", true,
                        YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT)));
        RangeGoalieResponse knight = creaseLine(8477970, "CHI", "1.0", "0.6");
        knight.getNights().getFirst().setStarter(RangeNight.StarterEnum.CONFIRMED);
        when(projectionServiceClient.rangeProjections(eq(MONDAY), eq(SUNDAY), anyInt()))
                .thenReturn(projections(List.of(), List.of(knight, creaseLine(8482821, "CHI", "0.0", "0.4"))));

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creases[0].goalies[0].nights[0].share").value(1.0))
                .andExpect(jsonPath("$.creases[0].goalies[0].nights[0].starter").value("confirmed"))
                // A night nobody has named, and the other goalie's night, say nothing.
                .andExpect(jsonPath("$.creases[0].goalies[0].nights[1].starter").doesNotExist())
                .andExpect(jsonPath("$.creases[0].goalies[1].nights[0].starter").doesNotExist());
    }

    @Test
    void sendsTheWholeCreaseOfEveryClubWithAGoalieListedTheRosteredOnesUnnamed() throws Exception {
        yahooWire(List.of(
                        yahooPlayer("5555", "Spencer Knight", "Chi", "G", true,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT),
                        yahooPlayer("7777", "Arvid Soderblom", "Chi", "G", true,
                                YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT)));
        when(projectionServiceClient.rangeProjections(eq(MONDAY), eq(SUNDAY), anyInt()))
                .thenReturn(projections(
                        List.of(),
                        List.of(
                                creaseLine(8482821, "CHI", "0.44", "0.44"),
                                // Rostered in this league: not a row, but his starts are starts
                                // the two listed do not get.
                                creaseLine(8470000, "CHI", "0.08", "0.08"),
                                creaseLine(8477970, "CHI", "0.48", "0.48"),
                                // A club with nobody listed has no crease to send.
                                creaseLine(8471111, "EDM", "1.0", "1.0"))));

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creases.length()").value(1))
                .andExpect(jsonPath("$.creases[0].team").value("CHI"))
                // The most starts first, which is how a tie is broken.
                .andExpect(jsonPath("$.creases[0].goalies.length()").value(3))
                .andExpect(jsonPath("$.creases[0].goalies[0].playerId").value("5555"))
                .andExpect(jsonPath("$.creases[0].goalies[0].nights[0].date").value("2026-10-13"))
                .andExpect(jsonPath("$.creases[0].goalies[0].nights[0].share").value(0.48))
                .andExpect(jsonPath("$.creases[0].goalies[0].nights[1].date").value("2026-10-15"))
                .andExpect(jsonPath("$.creases[0].goalies[1].playerId").value("7777"))
                .andExpect(jsonPath("$.creases[0].goalies[2].playerId").doesNotExist())
                .andExpect(jsonPath("$.creases[0].goalies[2].nights[0].share").value(0.08));
    }

    @Test
    void aLeagueWithNoGoalieListedHasNoCreases() throws Exception {
        yahooWire(List.of(yahooPlayer("3737", "Connor McDavid", "Edm", "C", false,
                        YahooAvailablePlayerResponse.AvailabilityEnum.FREE_AGENT)));

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.creases.length()").value(0));
    }

    @Test
    void aWaiversReadThatFailsFailsTheListWithItsOwnError() throws Exception {
        yahooWire(List.of());
        when(yahooServiceClient.leagueWaivers("user-1", "465.l.9", 300))
                .thenThrow(new YahooAccessDeniedException("Yahoo refused"));

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isFailedDependency());
    }

    @Test
    void readsAnEspnLeagueTheSameWayItsPoolLessItsRosters() throws Exception {
        SkaterResponse mcdavid = new SkaterResponse();
        mcdavid.setId(3895074L);
        mcdavid.setFirstName("Connor");
        mcdavid.setLastName("McDavid");
        mcdavid.setTeamAbbrev("EDM");
        mcdavid.setEligiblePositions(List.of("C"));
        SkaterResponse makar = new SkaterResponse();
        makar.setId(3900000L);
        makar.setFirstName("Cale");
        makar.setLastName("Makar");
        makar.setTeamAbbrev("COL");
        makar.setEligiblePositions(List.of("D"));
        when(espnServiceClient.skaters(anyInt())).thenReturn(List.of(mcdavid, makar));
        when(espnServiceClient.goalies(anyInt())).thenReturn(List.of());
        LeagueRosterPlayer heldMakar = new LeagueRosterPlayer();
        heldMakar.setEspnId(3900000L);
        heldMakar.setFullName("Cale Makar");
        heldMakar.setTeamAbbrev("COL");
        heldMakar.setEligiblePositions(List.of("D"));
        LeagueRosterTeam team = new LeagueRosterTeam();
        team.setName("Rival");
        team.setMine(false);
        team.setPlayers(List.of(heldMakar));
        LeagueRostersResponse rosters = new LeagueRostersResponse();
        rosters.setLeagueId("12345");
        rosters.setTeams(List.of(team));
        when(espnServiceClient.rosters("user-1", "12345")).thenReturn(rosters);
        when(espnServiceClient.leagueWaivers("user-1", "12345", 300)).thenReturn(List.of());
        when(projectionServiceClient.rangeProjections(eq(MONDAY), eq(SUNDAY), anyInt()))
                .thenReturn(projections(
                        List.of(fullSkaterLine(8478402, "EDM", "3.8"), fullSkaterLine(8480069, "COL", "3.9")),
                        List.of()));

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=ESPN&leagueId=12345&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                // Makar is held, so only McDavid is on the wire.
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.players[0].playerId").value("3895074"))
                .andExpect(jsonPath("$.players[0].name").value("Connor McDavid"))
                .andExpect(jsonPath("$.players[0].availability").value("FREE_AGENT"))
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
        yahooWire(List.of());

        mockMvc.perform(get("/api/v1/streamer-planner/free-agents?platform=YAHOO&leagueId=465.l.9&" + WEEK)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(0));
    }
}
