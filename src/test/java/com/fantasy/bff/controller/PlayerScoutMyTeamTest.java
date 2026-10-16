package com.fantasy.bff.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
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
import com.fantasy.bff.generated.projection.model.RestOfSeasonSkaterResponse;
import com.fantasy.bff.generated.projection.model.ServedRestOfSeasonSkater;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam;
import com.fantasy.bff.generated.yahoo.model.LeagueRostersResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import com.fantasy.bff.service.SeasonScheduleService;
import com.fantasy.bff.service.scoring.LeagueSchedule;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Player Scout's own-team read: the FA scout's roster join under Player Scout's switch, with every
 * club's remaining nights beside it so the client can seat the roster over the rest of the season.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "player-scout.enabled=true",
        "services.projection.player-mapping-ttl-ms=0",
        "players.league-pool-ttl-ms=0"
})
class PlayerScoutMyTeamTest extends BaseIntegrationTest {

    private static final String YAHOO_LEAGUE = "/api/v1/player-scout/my-team?platform=YAHOO&leagueId=465.l.9";

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
        PlayerResponse makar = new PlayerResponse();
        makar.setNhlId(8480069);
        makar.setFullName("Cale Makar");
        makar.setCurrentTeam("COL");
        makar.setSweaterNumber(8);
        makar.setIsActive(true);
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(makar));
        when(projectionServiceClient.retiredPlayers()).thenReturn(List.of());
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        RestOfSeasonSkaterResponse row = new RestOfSeasonSkaterResponse();
        row.setNhlId(8480069);
        row.setTargetSeason(2026);
        row.setModelVersion("marcel-v118");
        row.setGamesRemaining(76);
        ServedRestOfSeasonSkater served = new ServedRestOfSeasonSkater();
        served.setGamesPlayed(new BigDecimal("74"));
        served.setPoints(new BigDecimal("90.0"));
        row.setServed(served);
        when(projectionServiceClient.restOfSeasonSkaters(2026)).thenReturn(List.of(row));
        when(projectionServiceClient.restOfSeasonGoalies(2026)).thenReturn(List.of());
        when(scheduleService.schedule()).thenReturn(new LeagueSchedule(
                Map.of(
                        "COL", List.of(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 17)),
                        "BOS", List.of(LocalDate.of(2026, 10, 16))),
                Map.of("COL", 84, "BOS", 84)));

        LeagueRosterPlayer player = new LeagueRosterPlayer();
        player.setPlayerKey("465.p.6743");
        player.setPlayerId(6743);
        player.setFullName("Cale Makar");
        player.setTeamAbbrev("Col");
        player.setGoalie(false);
        player.setEligiblePositions(List.of("D"));
        player.setSelectedPosition("D");
        player.setUniformNumber(8);
        LeagueRosterTeam team = new LeagueRosterTeam();
        team.setTeamKey("465.l.9.t.2");
        team.setName("Slapshots");
        team.setMine(true);
        team.setPlayers(List.of(player));
        LeagueRostersResponse rosters = new LeagueRostersResponse();
        rosters.setLeagueKey("465.l.9");
        rosters.setTeams(List.of(team));
        when(yahooServiceClient.rosters("user-1", "465.l.9")).thenReturn(rosters);
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE)).andExpect(status().isUnauthorized());
    }

    @Test
    void readsTheUsersTeamWithEachPlayersRestOfTheSeasonAndTheClubsNights() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.teamName").value("Slapshots"))
                .andExpect(jsonPath("$.players", hasSize(1)))
                .andExpect(jsonPath("$.players[0].playerId").value("6743"))
                .andExpect(jsonPath("$.players[0].restOfSeason.games").value(74.0))
                .andExpect(jsonPath("$.players[0].restOfSeason.stats.points").value(90.0))
                .andExpect(jsonPath("$.players[0].reserve").value(false))
                // Every club's nights, by club, earliest first.
                .andExpect(jsonPath("$.schedule", hasSize(2)))
                .andExpect(jsonPath("$.schedule[0].team").value("BOS"))
                .andExpect(jsonPath("$.schedule[0].dates", contains("2026-10-16")))
                .andExpect(jsonPath("$.schedule[1].team").value("COL"))
                .andExpect(jsonPath("$.schedule[1].dates", contains("2026-10-15", "2026-10-17")));
    }

    @Test
    void aLeagueWithoutTheUsersTeamIsNotFoundButStillCarriesTheSchedule() throws Exception {
        LeagueRostersResponse rosters = new LeagueRostersResponse();
        rosters.setLeagueKey("465.l.9");
        rosters.setTeams(List.of());
        when(yahooServiceClient.rosters("user-1", "465.l.9")).thenReturn(rosters);

        mockMvc.perform(get(YAHOO_LEAGUE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.players", hasSize(0)))
                .andExpect(jsonPath("$.schedule", hasSize(2)));
    }
}
