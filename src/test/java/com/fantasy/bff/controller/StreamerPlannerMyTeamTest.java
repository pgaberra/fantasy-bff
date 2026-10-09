package com.fantasy.bff.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.RangeGoalieResponse;
import com.fantasy.bff.generated.projection.model.RangeProjectionsResponse;
import com.fantasy.bff.generated.projection.model.RangeSkaterResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam;
import com.fantasy.bff.generated.yahoo.model.LeagueRostersResponse;
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
 * The planner's own-team read: the user's team found by the platform's "mine" flag, each player
 * with the club and positions the client places him by, and "out" deciding who fills no slot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // The mapping is cached in a singleton; zero means "always stale", so each test's stubs win.
        "services.projection.player-mapping-ttl-ms=0"
})
class StreamerPlannerMyTeamTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private YahooServiceClient yahooServiceClient;
    @MockitoBean private EspnServiceClient espnServiceClient;
    @MockitoBean private ProjectionServiceClient projectionServiceClient;
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

    private static LeagueRosterPlayer yahooPlayer(
            int id, String name, String team, List<String> positions, String slot, String status) {
        LeagueRosterPlayer player = new LeagueRosterPlayer();
        player.setPlayerKey("465.p." + id);
        player.setPlayerId(id);
        player.setFullName(name);
        player.setTeamAbbrev(team);
        player.setGoalie(positions.contains("G"));
        player.setEligiblePositions(positions);
        player.setSelectedPosition(slot);
        player.setStatus(status);
        return player;
    }

    private static LeagueRosterTeam yahooTeam(String key, String name, boolean mine, List<LeagueRosterPlayer> players) {
        LeagueRosterTeam team = new LeagueRosterTeam();
        team.setTeamKey(key);
        team.setName(name);
        team.setMine(mine);
        team.setPlayers(players);
        return team;
    }

    @Test
    void readsTheUsersYahooTeamWithEachPlayersClubPositionsAndWhetherHeIsOut() throws Exception {
        LeagueRostersResponse rosters = new LeagueRostersResponse();
        rosters.setLeagueKey("465.l.9");
        rosters.setTeams(List.of(
                yahooTeam("465.l.9.t.1", "Someone Else", false,
                        List.of(yahooPlayer(1, "Not Mine", "BOS", List.of("C"), "C", null))),
                yahooTeam("465.l.9.t.2", "My Team", true, List.of(
                        yahooPlayer(6743, "Connor McDavid", "EDM", List.of("C", "Util"), "C", null),
                        yahooPlayer(7000, "Hurt Wing", "TB", List.of("LW", "RW"), "BN", "DTD"),
                        yahooPlayer(7001, "Parked Man", "SJ", List.of("D"), "IR+", "IR"),
                        yahooPlayer(7002, "Out Goalie", "CHI", List.of("G"), "G", "O")))));
        when(yahooServiceClient.rosters("user-1", "465.l.9")).thenReturn(rosters);

        mockMvc.perform(get("/api/v1/streamer-planner/my-team?platform=YAHOO&leagueId=465.l.9")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.teamName").value("My Team"))
                .andExpect(jsonPath("$.players.length()").value(4))
                .andExpect(jsonPath("$.players[0].playerId").value("6743"))
                .andExpect(jsonPath("$.players[0].teamAbbrev").value("EDM"))
                .andExpect(jsonPath("$.players[0].type").value("skater"))
                // Util is a slot, not a position.
                .andExpect(jsonPath("$.players[0].positions.length()").value(1))
                .andExpect(jsonPath("$.players[0].out").value(false))
                // Day-to-day still plays.
                .andExpect(jsonPath("$.players[1].injuryStatus").value("DTD"))
                .andExpect(jsonPath("$.players[1].out").value(false))
                .andExpect(jsonPath("$.players[2].slot").value("IR+"))
                .andExpect(jsonPath("$.players[2].out").value(true))
                .andExpect(jsonPath("$.players[3].type").value("goalie"))
                .andExpect(jsonPath("$.players[3].out").value(true));
        verifyNoInteractions(espnServiceClient);
    }

    @Test
    void readsTheUsersEspnTeam() throws Exception {
        var player = new com.fantasy.bff.generated.espn.model.LeagueRosterPlayer();
        player.setEspnId(3114727L);
        player.setFullName("Cale Makar");
        player.setTeamAbbrev("COL");
        player.setGoalie(false);
        player.setEligiblePositions(List.of("D"));
        player.setLineupSlot("IR");
        player.setInjuryStatus("OUT");
        var team = new com.fantasy.bff.generated.espn.model.LeagueRosterTeam();
        team.setTeamId(2);
        team.setName("Bravo");
        team.setMine(true);
        team.setPlayerIds(List.of(3114727L));
        team.setPlayers(List.of(player));
        var rosters = new com.fantasy.bff.generated.espn.model.LeagueRostersResponse();
        rosters.setTeams(List.of(team));
        when(espnServiceClient.rosters("user-1", "123")).thenReturn(rosters);

        mockMvc.perform(get("/api/v1/streamer-planner/my-team?platform=ESPN&leagueId=123")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.players[0].playerId").value("3114727"))
                .andExpect(jsonPath("$.players[0].positions[0]").value("D"))
                .andExpect(jsonPath("$.players[0].out").value(true));
    }

    @Test
    void aLeagueWithNoTeamOfTheUsersIsNotFoundRatherThanAnError() throws Exception {
        LeagueRostersResponse rosters = new LeagueRostersResponse();
        rosters.setLeagueKey("465.l.9");
        rosters.setTeams(List.of(yahooTeam("465.l.9.t.1", "Someone Else", false, List.of())));
        when(yahooServiceClient.rosters("user-1", "465.l.9")).thenReturn(rosters);

        mockMvc.perform(get("/api/v1/streamer-planner/my-team?platform=YAHOO&leagueId=465.l.9")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.players.length()").value(0));
    }

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);

    private void stubYahooTeamWithASkaterAGoalieAndAStranger() {
        LeagueRosterPlayer mcdavid = yahooPlayer(6743, "Connor McDavid", "EDM", List.of("C"), "C", null);
        mcdavid.setUniformNumber(97);
        LeagueRosterPlayer knight = yahooPlayer(5555, "Spencer Knight", "CHI", List.of("G"), "G", null);
        knight.setUniformNumber(30);
        LeagueRostersResponse rosters = new LeagueRostersResponse();
        rosters.setLeagueKey("465.l.9");
        rosters.setTeams(List.of(yahooTeam("465.l.9.t.2", "My Team", true, List.of(
                mcdavid,
                knight,
                yahooPlayer(9999, "Nobody Projected", "SJ", List.of("D"), "BN", null)))));
        when(yahooServiceClient.rosters("user-1", "465.l.9")).thenReturn(rosters);
    }

    @Test
    void givenAStretchEachProjectedPlayerCarriesTheModelsLineOverItOnAFreeAgentsScale() throws Exception {
        stubYahooTeamWithASkaterAGoalieAndAStranger();
        RangeSkaterResponse skater = new RangeSkaterResponse();
        skater.setNhlId(8478402);
        skater.setTeam("EDM");
        skater.setClubGames(4);
        skater.setExpectedGames(new BigDecimal("3.8"));
        skater.setPoints(new BigDecimal("6.1"));
        skater.setPpPoints(new BigDecimal("2.0"));
        skater.setShPoints(new BigDecimal("0.2"));
        RangeGoalieResponse goalie = new RangeGoalieResponse();
        goalie.setNhlId(8477970);
        goalie.setTeam("CHI");
        goalie.setClubGames(3);
        goalie.setExpectedGames(new BigDecimal("2.1"));
        goalie.setWins(new BigDecimal("1.1"));
        goalie.setSavePct(new BigDecimal("0.908"));
        RangeProjectionsResponse projections = new RangeProjectionsResponse();
        projections.setSeason(2026);
        projections.setModelVersion("marcel-v16");
        projections.setStart(MONDAY);
        projections.setEnd(SUNDAY);
        projections.setSkaters(List.of(skater));
        projections.setGoalies(List.of(goalie));
        when(projectionServiceClient.rangeProjections(eq(MONDAY), eq(SUNDAY), anyInt())).thenReturn(projections);

        mockMvc.perform(get("/api/v1/streamer-planner/my-team?platform=YAHOO&leagueId=465.l.9"
                                + "&start=2026-10-12&end=2026-10-18")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(3))
                // The player the model does not project has no line rather than a line of zeros.
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].playerId").value("6743"))
                .andExpect(jsonPath("$.lines[0].clubGames").value(4))
                .andExpect(jsonPath("$.lines[0].expectedGames").value(3.8))
                .andExpect(jsonPath("$.lines[0].stats.points").value(6.1))
                .andExpect(jsonPath("$.lines[0].stats.stp").value(2.2))
                .andExpect(jsonPath("$.lines[1].playerId").value("5555"))
                .andExpect(jsonPath("$.lines[1].stats.gs").value(2.1))
                .andExpect(jsonPath("$.lines[1].stats.svPct").value(0.908));
    }

    @Test
    void withoutAStretchTheModelIsNotAskedAndThereAreNoLines() throws Exception {
        stubYahooTeamWithASkaterAGoalieAndAStranger();

        mockMvc.perform(get("/api/v1/streamer-planner/my-team?platform=YAHOO&leagueId=465.l.9")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(3))
                .andExpect(jsonPath("$.lines.length()").value(0));
        verify(projectionServiceClient, never()).rangeProjections(any(), any(), anyInt());
    }

    @Test
    void aStartWithoutAnEndIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/streamer-planner/my-team?platform=YAHOO&leagueId=465.l.9&start=2026-10-12")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(yahooServiceClient);
    }
}
