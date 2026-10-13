package com.fantasy.bff.controller;

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
import com.fantasy.bff.generated.projection.model.PlayerResponse;
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
 * Start/Bench's read: the user's team as the platform has it set for the day asked for, each
 * player the model projects with its line for that day alone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "start-bench.enabled=true",
        // The mapping is cached in a singleton; zero means "always stale", so each test's stubs win.
        "services.projection.player-mapping-ttl-ms=0"
})
class StartBenchTest extends BaseIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

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
        PlayerResponse mcdavid = new PlayerResponse();
        mcdavid.setNhlId(8478402);
        mcdavid.setFullName("Connor McDavid");
        mcdavid.setCurrentTeam("EDM");
        mcdavid.setSweaterNumber(97);
        mcdavid.setIsActive(true);
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(mcdavid));
        when(projectionServiceClient.retiredPlayers()).thenReturn(List.of());
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
    }

    private static LeagueRosterPlayer yahooPlayer(int id, String name, String team, String position, String slot) {
        LeagueRosterPlayer player = new LeagueRosterPlayer();
        player.setPlayerKey("465.p." + id);
        player.setPlayerId(id);
        player.setFullName(name);
        player.setTeamAbbrev(team);
        player.setGoalie(false);
        player.setEligiblePositions(List.of(position));
        player.setSelectedPosition(slot);
        return player;
    }

    @Test
    void readsTheTeamAsSetForTheDayWithTheModelsLineForThatDayAlone() throws Exception {
        LeagueRosterPlayer mcdavid = yahooPlayer(6743, "Connor McDavid", "EDM", "C", "BN");
        mcdavid.setUniformNumber(97);
        LeagueRosterTeam team = new LeagueRosterTeam();
        team.setTeamKey("465.l.9.t.2");
        team.setName("My Team");
        team.setMine(true);
        team.setPlayers(List.of(mcdavid, yahooPlayer(9999, "Nobody Projected", "SJ", "D", "D")));
        LeagueRostersResponse rosters = new LeagueRostersResponse();
        rosters.setLeagueKey("465.l.9");
        rosters.setTeams(List.of(team));
        when(yahooServiceClient.rosters("user-1", "465.l.9", DAY)).thenReturn(rosters);

        RangeSkaterResponse line = new RangeSkaterResponse();
        line.setNhlId(8478402);
        line.setTeam("EDM");
        line.setClubGames(1);
        line.setExpectedGames(new BigDecimal("0.97"));
        line.setPoints(new BigDecimal("1.6"));
        RangeProjectionsResponse projections = new RangeProjectionsResponse();
        projections.setSeason(2026);
        projections.setModelVersion("marcel-v16");
        projections.setStart(DAY);
        projections.setEnd(DAY);
        projections.setSkaters(List.of(line));
        projections.setGoalies(List.of());
        when(projectionServiceClient.rangeProjections(eq(DAY), eq(DAY), anyInt())).thenReturn(projections);

        mockMvc.perform(get("/api/v1/start-bench/my-team?platform=YAHOO&leagueId=465.l.9&date=2026-10-14")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.players.length()").value(2))
                // The slot the platform has him in that day, not the one he holds today.
                .andExpect(jsonPath("$.players[0].slot").value("BN"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].playerId").value("6743"))
                .andExpect(jsonPath("$.lines[0].clubGames").value(1))
                .andExpect(jsonPath("$.lines[0].expectedGames").value(0.97))
                .andExpect(jsonPath("$.lines[0].stats.points").value(1.6));
        // The roster as it stands today is never read in its place.
        verify(yahooServiceClient, never()).rosters(anyString(), anyString());
        verify(espnServiceClient, never()).rosters(anyString(), anyString(), any());
    }

    @Test
    void readsAnEspnTeamForTheDayToo() throws Exception {
        var player = new com.fantasy.bff.generated.espn.model.LeagueRosterPlayer();
        player.setEspnId(3114727L);
        player.setFullName("Cale Makar");
        player.setTeamAbbrev("COL");
        player.setGoalie(false);
        player.setEligiblePositions(List.of("D"));
        player.setLineupSlot("D");
        var team = new com.fantasy.bff.generated.espn.model.LeagueRosterTeam();
        team.setTeamId(2);
        team.setName("Bravo");
        team.setMine(true);
        team.setPlayerIds(List.of(3114727L));
        team.setPlayers(List.of(player));
        var rosters = new com.fantasy.bff.generated.espn.model.LeagueRostersResponse();
        rosters.setTeams(List.of(team));
        when(espnServiceClient.rosters("user-1", "123", DAY)).thenReturn(rosters);

        mockMvc.perform(get("/api/v1/start-bench/my-team?platform=ESPN&leagueId=123&date=2026-10-14")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].playerId").value("3114727"))
                .andExpect(jsonPath("$.players[0].slot").value("D"));
    }

    @Test
    void withoutADateItIsABadRequestAndNothingIsRead() throws Exception {
        mockMvc.perform(get("/api/v1/start-bench/my-team?platform=YAHOO&leagueId=465.l.9")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(yahooServiceClient, espnServiceClient);
    }

    @Test
    void aDateThatIsNotOneIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/start-bench/my-team?platform=YAHOO&leagueId=465.l.9&date=tomorrow")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(yahooServiceClient, espnServiceClient);
    }

    @Test
    void theFeatureIsReportedOn() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startBench").value(true));
    }
}
