package com.fantasy.bff.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam;
import com.fantasy.bff.generated.yahoo.model.LeagueRostersResponse;
import com.fantasy.bff.security.JwtTokenValidator;
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
@TestPropertySource(properties = {"streamer-planner.enabled=true", "streamer-planner.my-team-enabled=true"})
class StreamerPlannerMyTeamTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private YahooServiceClient yahooServiceClient;
    @MockitoBean private EspnServiceClient espnServiceClient;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");
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

    @Test
    void theFeatureIsReportedOn() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streamerPlanner").value(true))
                .andExpect(jsonPath("$.streamerPlannerMyTeam").value(true));
    }
}
