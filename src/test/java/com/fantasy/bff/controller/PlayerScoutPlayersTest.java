package com.fantasy.bff.controller;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.fantasy.bff.generated.projection.model.LineupListingResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonGoalieResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonSkaterResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonSnapshotsResponse;
import com.fantasy.bff.generated.projection.model.RoleChangesResponse;
import com.fantasy.bff.generated.projection.model.RoleWindowResponse;
import com.fantasy.bff.generated.projection.model.ServedRestOfSeasonGoalie;
import com.fantasy.bff.generated.projection.model.ServedRestOfSeasonSkater;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.generated.projection.model.SkaterRoleChangeResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam;
import com.fantasy.bff.generated.yahoo.model.LeagueRostersResponse;
import com.fantasy.bff.generated.yahoo.model.YahooAvailablePlayerResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import com.fantasy.bff.service.SeasonScheduleService;
import com.fantasy.bff.service.scoring.LeagueSchedule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Player Scout's league read: the wire and every roster joined to the model on identity, each
 * player with today's rest of the season, the line as it stood on the chosen day rescaled to the
 * games left, and his ice since. These tests pin the join, who holds whom, which earlier line
 * answers a day, and the rescaling arithmetic end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "player-scout.enabled=true",
        // The mapping is cached in a singleton; zero means "always stale", so each test's stubs win.
        "services.projection.player-mapping-ttl-ms=0"
})
class PlayerScoutPlayersTest extends BaseIntegrationTest {

    private static final String YAHOO_LEAGUE = "/api/v1/player-scout/players?platform=YAHOO&leagueId=465.l.9";
    private static final LocalDate KEPT_DAY = LocalDate.of(2026, 10, 27);

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private ProjectionServiceClient projectionServiceClient;
    @MockitoBean private YahooServiceClient yahooServiceClient;
    @MockitoBean private EspnServiceClient espnServiceClient;
    @MockitoBean private PlayerServiceClient playerServiceClient;
    @MockitoBean private SeasonScheduleService scheduleService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(
                identity(8476875, "Brandon Montour", "SEA", 62),
                identity(8480069, "Cale Makar", "COL", 8),
                identity(8477492, "Nathan MacKinnon", "COL", 29),
                identity(8481611, "Logan Stankoven", "CAR", 11),
                identity(8477970, "Spencer Knight", "CHI", 30)));
        when(projectionServiceClient.retiredPlayers()).thenReturn(List.of());
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        when(projectionServiceClient.restOfSeasonSkaters(2026)).thenReturn(List.of(
                restOfSeason(8476875, "SEA", 78, "70", "1380", "48.5"),
                restOfSeason(8481611, "CAR", 78, "72", "1010", "38.0"),
                restOfSeason(8480069, "COL", 76, "74", "1500", "90.0"),
                restOfSeason(8477492, "COL", 76, "74", "1320", "110.0")));
        when(projectionServiceClient.restOfSeasonGoalies(2026)).thenReturn(List.of(knightRestOfSeason(78)));
        when(projectionServiceClient.restOfSeasonSnapshots(2026)).thenReturn(snapshots(KEPT_DAY));
        when(projectionServiceClient.restOfSeasonSkaters(eq(2026), any(LocalDate.class))).thenReturn(List.of());
        when(projectionServiceClient.restOfSeasonGoalies(eq(2026), any(LocalDate.class))).thenReturn(List.of());
        when(projectionServiceClient.skaterProjections(2026, "preseason")).thenReturn(List.of(
                preseason(8476875, "80", "1050", "30.0"),
                preseason(8480069, "82", "1500", "92.0")));
        when(projectionServiceClient.goalieProjections(2026, "preseason")).thenReturn(List.of());
        when(projectionServiceClient.skaterRoleChangesSince(eq(2026), any(LocalDate.class)))
                .thenReturn(roleChanges(8476875));
        when(scheduleService.schedule()).thenReturn(new LeagueSchedule(
                Map.of("SEA", List.of(), "COL", List.of()), Map.of("SEA", 84, "COL", 84)));
        when(yahooServiceClient.leagueFreeAgents(eq("user-1"), eq("465.l.9"), anyString(), anyInt()))
                .thenReturn(List.of(
                        yahooPlayer("1001", "Brandon Montour", "Sea", "D", false),
                        yahooPlayer("1002", "Logan Stankoven", "Car", "RW", false),
                        yahooPlayer("1003", "Spencer Knight", "Chi", "G", true)));
        LeagueRostersResponse rosters = new LeagueRostersResponse();
        rosters.setLeagueKey("465.l.9");
        rosters.setTeams(List.of(
                yahooTeam("Someone Else", false, List.of(
                        rostered(9001, "Nathan MacKinnon", "Col", List.of("C"), "C", null, 29))),
                yahooTeam("Slapshots", true, List.of(
                        rostered(6743, "Cale Makar", "Col", List.of("D"), "D", "DTD", 8)))));
        when(yahooServiceClient.rosters("user-1", "465.l.9")).thenReturn(rosters);
    }

    @Test
    void theFeatureIsReportedOn() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerScout").value(true));
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE)).andExpect(status().isUnauthorized());
    }

    @Test
    void listsTheWireAndEveryRosterWithWhoHoldsWhomAndBothLines() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value(2026))
                .andExpect(jsonPath("$.modelVersion").value("marcel-v118"))
                .andExpect(jsonPath("$.inSeason").value(true))
                // No day asked for: the preseason line answers, and the history's first day is told.
                .andExpect(jsonPath("$.since.kind").value("PRESEASON"))
                .andExpect(jsonPath("$.since.requested").value(nullValue()))
                .andExpect(jsonPath("$.since.resolved").value(nullValue()))
                .andExpect(jsonPath("$.since.earliestSnapshot").value("2026-10-27"))
                .andExpect(jsonPath("$.recentGames").value(nullValue()))
                // Three on the wire and two rostered, most games ahead first.
                .andExpect(jsonPath("$.players", hasSize(5)))
                .andExpect(jsonPath("$.players[0].name").value("Cale Makar"))
                .andExpect(jsonPath("$.players[0].nhlId").value(8480069))
                .andExpect(jsonPath("$.players[0].owner.teamName").value("Slapshots"))
                .andExpect(jsonPath("$.players[0].owner.mine").value(true))
                .andExpect(jsonPath("$.players[0].slot").value("D"))
                .andExpect(jsonPath("$.players[0].injuryStatus").value("DTD"))
                .andExpect(jsonPath("$.players[0].availability").value(nullValue()))
                .andExpect(jsonPath("$.players[0].gamesRemaining").value(76))
                .andExpect(jsonPath("$.players[0].now.stats.defPoints").value(90.0))
                .andExpect(jsonPath("$.players[1].name").value("Nathan MacKinnon"))
                .andExpect(jsonPath("$.players[1].owner.teamName").value("Someone Else"))
                .andExpect(jsonPath("$.players[1].owner.mine").value(false))
                // No preseason line for him: absent, not zero.
                .andExpect(jsonPath("$.players[1].then").value(nullValue()))
                .andExpect(jsonPath("$.players[2].name").value("Logan Stankoven"))
                .andExpect(jsonPath("$.players[2].owner").value(nullValue()))
                .andExpect(jsonPath("$.players[2].availability").value("FREE_AGENT"))
                .andExpect(jsonPath("$.players[3].playerId").value("1001"))
                .andExpect(jsonPath("$.players[3].name").value("Brandon Montour"))
                .andExpect(jsonPath("$.players[3].type").value("skater"))
                .andExpect(jsonPath("$.players[3].gamesRemaining").value(78))
                .andExpect(jsonPath("$.players[3].now.games").value(70.0))
                .andExpect(jsonPath("$.players[3].now.stats.points").value(48.5))
                // The preseason line re-spread over the 78 games left: 80 of 84 of them is 74.29
                // games, at 30 points over 80, so 27.86; the rate per game is carried as it is.
                .andExpect(jsonPath("$.players[3].then.games").value(closeTo(74.2857, 0.001)))
                .andExpect(jsonPath("$.players[3].then.stats.points").value(closeTo(27.857, 0.001)))
                .andExpect(jsonPath("$.players[3].then.stats.toiPerGame").value(1050.0))
                // His ice since the day, beside before it.
                .andExpect(jsonPath("$.players[3].usage.recent.games").value(4))
                .andExpect(jsonPath("$.players[3].usage.recent.toiPerGame").value(1380.0))
                .andExpect(jsonPath("$.players[3].usage.recent.ppShare").value(0.85))
                .andExpect(jsonPath("$.players[3].usage.baseline.toiPerGame").value(1050.0))
                .andExpect(jsonPath("$.players[3].usage.baselineSource").value("SEASON"))
                .andExpect(jsonPath("$.players[3].usage.listedNow.line").value("d1"))
                .andExpect(jsonPath("$.players[3].usage.listedNow.powerPlayUnit").value(1))
                .andExpect(jsonPath("$.players[3].usage.listedBefore.line").value("d3"))
                .andExpect(jsonPath("$.players[2].usage").value(nullValue()))
                .andExpect(jsonPath("$.players[4].name").value("Spencer Knight"))
                .andExpect(jsonPath("$.players[4].type").value("goalie"))
                .andExpect(jsonPath("$.players[4].now.games").value(50.0))
                .andExpect(jsonPath("$.players[4].usage").value(nullValue()));
    }

    @Test
    void theIceIsReadOverTheClubsLastGamesWhenAskedFor() throws Exception {
        when(projectionServiceClient.skaterRoleChanges(2026, 5)).thenReturn(roleChanges(8481611));

        mockMvc.perform(get(YAHOO_LEAGUE + "&recentGames=5").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentGames").value(5))
                // Stankoven's ice came from the fixed window, Montour's since-the-day read was not made.
                .andExpect(jsonPath("$.players[2].name").value("Logan Stankoven"))
                .andExpect(jsonPath("$.players[2].usage.recent.games").value(4))
                .andExpect(jsonPath("$.players[3].usage").value(nullValue()));
        verify(projectionServiceClient, never()).skaterRoleChangesSince(anyInt(), any(LocalDate.class));
    }

    @Test
    void anIceWindowOutsideItsBoundsIsRefused() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE + "&recentGames=0").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(YAHOO_LEAGUE + "&recentGames=21").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDayTheHistoryKeepsIsAnsweredWithThatDaysLineRescaledToTheGamesLeft() throws Exception {
        when(projectionServiceClient.restOfSeasonSkaters(2026, KEPT_DAY)).thenReturn(List.of(
                // Kept with 73 club games left, 69 of them his, 55 points over them.
                restOfSeason(8476875, "SEA", 73, "69", "1200", "55.0")));
        when(projectionServiceClient.restOfSeasonGoalies(2026, KEPT_DAY)).thenReturn(List.of(knightRestOfSeason(73)));

        mockMvc.perform(get(YAHOO_LEAGUE + "&since=2026-10-27").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.since.kind").value("SNAPSHOT"))
                .andExpect(jsonPath("$.since.requested").value("2026-10-27"))
                .andExpect(jsonPath("$.since.resolved").value("2026-10-27"))
                // 69 of 73 over the 78 left today is 73.73 games; 55 over 69 of them is 58.77.
                .andExpect(jsonPath("$.players[3].name").value("Brandon Montour"))
                .andExpect(jsonPath("$.players[3].then.games").value(closeTo(73.726, 0.001)))
                .andExpect(jsonPath("$.players[3].then.stats.points").value(closeTo(58.768, 0.001)))
                .andExpect(jsonPath("$.players[3].then.stats.toiPerGame").value(1200.0))
                // Nobody else was kept that day: the preseason line does not fill in for them.
                .andExpect(jsonPath("$.players[0].name").value("Cale Makar"))
                .andExpect(jsonPath("$.players[0].then").value(nullValue()))
                // A goalie's starts are the games rescaled: 50 of 73 over 78.
                .andExpect(jsonPath("$.players[4].then.games").value(closeTo(53.425, 0.001)))
                .andExpect(jsonPath("$.players[4].then.stats.svPct").value(0.905));
    }

    @Test
    void aDayBeforeTheHistoryBeganIsAnsweredWithThePreseasonLine() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE + "&since=2026-10-10").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.since.kind").value("PRESEASON"))
                .andExpect(jsonPath("$.since.requested").value("2026-10-10"))
                .andExpect(jsonPath("$.since.resolved").value(nullValue()))
                .andExpect(jsonPath("$.since.earliestSnapshot").value("2026-10-27"))
                .andExpect(jsonPath("$.players[3].then.stats.toiPerGame").value(1050.0));
    }

    @Test
    void withoutAPreseasonSnapshotOrAKeptDayNobodyHasAThenLine() throws Exception {
        when(projectionServiceClient.skaterProjections(2026, "preseason")).thenReturn(List.of());
        when(projectionServiceClient.restOfSeasonSnapshots(2026)).thenReturn(snapshots());

        mockMvc.perform(get(YAHOO_LEAGUE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.since.kind").value("NONE"))
                .andExpect(jsonPath("$.since.earliestSnapshot").value(nullValue()))
                .andExpect(jsonPath("$.players[3].then").value(nullValue()));
    }

    @Test
    void outOfSeasonListsNobodyAndDoesNotAskThePlatform() throws Exception {
        when(projectionServiceClient.restOfSeasonSkaters(2026)).thenReturn(List.of());
        when(projectionServiceClient.restOfSeasonGoalies(2026)).thenReturn(List.of());

        mockMvc.perform(get(YAHOO_LEAGUE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inSeason").value(false))
                .andExpect(jsonPath("$.since.kind").value("NONE"))
                .andExpect(jsonPath("$.players", hasSize(0)));
        verifyNoInteractions(yahooServiceClient);
    }

    @Test
    void aMalformedDayOrAnOversizedLeagueIdIsRefused() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE + "&since=last-week").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/player-scout/players?platform=YAHOO&leagueId=" + "x".repeat(65))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(yahooServiceClient);
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
            int nhlId, String team, int remaining, String games, String toi, String points) {
        RestOfSeasonSkaterResponse row = new RestOfSeasonSkaterResponse();
        row.setNhlId(nhlId);
        row.setTargetSeason(2026);
        row.setModelVersion("marcel-v118");
        row.setTeam(team);
        row.setGamesRemaining(remaining);
        row.setGeneratedAt(OffsetDateTime.of(KEPT_DAY.atTime(4, 30), ZoneOffset.UTC));
        ServedRestOfSeasonSkater served = new ServedRestOfSeasonSkater();
        served.setGamesPlayed(new BigDecimal(games));
        served.setToiPerGameSeconds(new BigDecimal(toi));
        served.setPoints(new BigDecimal(points));
        row.setServed(served);
        return row;
    }

    private static SkaterProjectionResponse preseason(int nhlId, String games, String toi, String points) {
        return new SkaterProjectionResponse()
                .nhlId(nhlId)
                .targetSeason(2026)
                .modelVersion("preseason")
                .gamesPlayed(new BigDecimal(games))
                .toiPerGameSeconds(new BigDecimal(toi))
                .points(new BigDecimal(points));
    }

    private static RestOfSeasonGoalieResponse knightRestOfSeason(int remaining) {
        RestOfSeasonGoalieResponse row = new RestOfSeasonGoalieResponse();
        row.setNhlId(8477970);
        row.setTargetSeason(2026);
        row.setModelVersion("marcel-v118");
        row.setTeam("CHI");
        row.setGamesRemaining(remaining);
        row.setGeneratedAt(OffsetDateTime.of(KEPT_DAY.atTime(4, 30), ZoneOffset.UTC));
        ServedRestOfSeasonGoalie served = new ServedRestOfSeasonGoalie();
        served.setGamesPlayed(new BigDecimal("52"));
        served.setGamesStarted(new BigDecimal("50"));
        served.setWins(new BigDecimal("24"));
        served.setSavePct(new BigDecimal("0.905"));
        row.setServed(served);
        return row;
    }

    private static RestOfSeasonSnapshotsResponse snapshots(LocalDate... days) {
        RestOfSeasonSnapshotsResponse response = new RestOfSeasonSnapshotsResponse();
        response.setSeason(2026);
        response.setDays(List.of(days));
        return response;
    }

    private static RoleChangesResponse roleChanges(int nhlId) {
        SkaterRoleChangeResponse skater = new SkaterRoleChangeResponse();
        skater.setNhlId(nhlId);
        skater.setTeam("SEA");
        skater.setRecent(window(4, "1380", "0.85"));
        skater.setBaseline(window(6, "1050", "0.20"));
        skater.setBaselineSource(SkaterRoleChangeResponse.BaselineSourceEnum.SEASON);
        skater.setFirstRecentGameDate(LocalDate.of(2026, 10, 21));
        skater.setLastRecentGameDate(LocalDate.of(2026, 10, 27));
        skater.setListedNow(listing(LocalDate.of(2026, 10, 27), "d1", 1));
        skater.setListedBefore(listing(LocalDate.of(2026, 10, 7), "d3", null));
        RoleChangesResponse response = new RoleChangesResponse();
        response.setSeason(2026);
        response.setRecentGames(5);
        response.setSkaters(List.of(skater));
        return response;
    }

    private static RoleWindowResponse window(int games, String toi, String share) {
        RoleWindowResponse window = new RoleWindowResponse();
        window.setGames(games);
        window.setToiPerGame(new BigDecimal(toi));
        window.setPpToiPerGame(new BigDecimal("200"));
        window.setPpShare(new BigDecimal(share));
        return window;
    }

    private static LineupListingResponse listing(LocalDate day, String line, Integer unit) {
        LineupListingResponse listing = new LineupListingResponse();
        listing.setSeenOn(day);
        listing.setLine(line);
        listing.setPowerPlayUnit(unit);
        listing.setOutOfLineup(false);
        return listing;
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

    private static LeagueRosterPlayer rostered(
            int id, String name, String team, List<String> positions, String slot, String status, int number) {
        LeagueRosterPlayer player = new LeagueRosterPlayer();
        player.setPlayerKey("465.p." + id);
        player.setPlayerId(id);
        player.setFullName(name);
        player.setTeamAbbrev(team);
        player.setGoalie(positions.contains("G"));
        player.setEligiblePositions(positions);
        player.setSelectedPosition(slot);
        player.setStatus(status);
        player.setUniformNumber(number);
        return player;
    }

    private static LeagueRosterTeam yahooTeam(String name, boolean mine, List<LeagueRosterPlayer> players) {
        LeagueRosterTeam team = new LeagueRosterTeam();
        team.setTeamKey("465.l.9.t." + name.length());
        team.setName(name);
        team.setMine(mine);
        team.setPlayers(players);
        return team;
    }
}
