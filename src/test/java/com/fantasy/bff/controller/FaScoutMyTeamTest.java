package com.fantasy.bff.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
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
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonGoalieResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonSkaterResponse;
import com.fantasy.bff.generated.projection.model.ServedRestOfSeasonGoalie;
import com.fantasy.bff.generated.projection.model.ServedRestOfSeasonSkater;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterPlayer;
import com.fantasy.bff.generated.yahoo.model.LeagueRosterTeam;
import com.fantasy.bff.generated.yahoo.model.LeagueRostersResponse;
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
 * The FA scout's own-team endpoint: the user's roster read live, joined to the model on identity as
 * the wire is, each player with the same rest-of-season line the available players carry. These
 * tests pin that join and what tells the client a player makes no room when dropped.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "fa-scout.enabled=true",
        // The mapping is cached in a singleton; zero means "always stale", so each test's stubs win.
        "services.projection.player-mapping-ttl-ms=0"
})
class FaScoutMyTeamTest extends BaseIntegrationTest {

    private static final String YAHOO_LEAGUE = "/api/v1/fa-scout/my-team?platform=YAHOO&leagueId=465.l.9";

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
                identity(8480069, "Cale Makar", "COL", 8),
                identity(8478402, "Connor McDavid", "EDM", 97),
                identity(8477970, "Spencer Knight", "CHI", 30)));
        when(projectionServiceClient.retiredPlayers()).thenReturn(List.of());
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        when(projectionServiceClient.restOfSeasonSkaters(2026)).thenReturn(List.of(
                restOfSeason(8480069, "74", "90.0"),
                restOfSeason(8478402, "40", "70.0")));
        when(projectionServiceClient.restOfSeasonGoalies(2026)).thenReturn(List.of(knightRestOfSeason()));

        LeagueRostersResponse rosters = new LeagueRostersResponse();
        rosters.setLeagueKey("465.l.9");
        rosters.setTeams(List.of(
                yahooTeam("465.l.9.t.1", "Someone Else", false, List.of(
                        yahooPlayer(9001, "Nathan MacKinnon", "Col", List.of("C"), "C", null, 29))),
                yahooTeam("465.l.9.t.2", "Slapshots", true, List.of(
                        yahooPlayer(6743, "Cale Makar", "Col", List.of("D"), "D", null, 8),
                        yahooPlayer(6744, "Connor McDavid", "Edm", List.of("C"), "IR+", "IR", 97),
                        yahooPlayer(6745, "Spencer Knight", "Chi", List.of("G"), "BN", "DTD", 30),
                        yahooPlayer(6747, "Filip Hronek", "Van", List.of("D", "IR"), "BN", "O", 17),
                        yahooPlayer(6746, "Unknown Prospect", "Chi", List.of("LW"), "NA", "NA", 77)))));
        when(yahooServiceClient.rosters("user-1", "465.l.9")).thenReturn(rosters);
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

    private static RestOfSeasonSkaterResponse restOfSeason(int nhlId, String games, String points) {
        RestOfSeasonSkaterResponse row = new RestOfSeasonSkaterResponse();
        row.setNhlId(nhlId);
        row.setTargetSeason(2026);
        row.setModelVersion("marcel-v118");
        row.setGamesRemaining(78);
        ServedRestOfSeasonSkater served = new ServedRestOfSeasonSkater();
        served.setGamesPlayed(new BigDecimal(games));
        served.setPoints(new BigDecimal(points));
        row.setServed(served);
        return row;
    }

    private static RestOfSeasonGoalieResponse knightRestOfSeason() {
        RestOfSeasonGoalieResponse row = new RestOfSeasonGoalieResponse();
        row.setNhlId(8477970);
        row.setTargetSeason(2026);
        row.setModelVersion("marcel-v118");
        row.setGamesRemaining(78);
        ServedRestOfSeasonGoalie served = new ServedRestOfSeasonGoalie();
        served.setGamesPlayed(new BigDecimal("52"));
        served.setGamesStarted(new BigDecimal("50"));
        served.setWins(new BigDecimal("24"));
        row.setServed(served);
        return row;
    }

    private static LeagueRosterPlayer yahooPlayer(
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

    private static LeagueRosterTeam yahooTeam(String key, String name, boolean mine, List<LeagueRosterPlayer> players) {
        LeagueRosterTeam team = new LeagueRosterTeam();
        team.setTeamKey(key);
        team.setName(name);
        team.setMine(mine);
        team.setPlayers(players);
        return team;
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE)).andExpect(status().isUnauthorized());
    }

    @Test
    void readsTheUsersTeamWithEachPlayersRestOfTheSeason() throws Exception {
        mockMvc.perform(get(YAHOO_LEAGUE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.teamName").value("Slapshots"))
                // His own five, in the platform's order; the other team's player is not one.
                .andExpect(jsonPath("$.players", hasSize(5)))
                .andExpect(jsonPath("$.players[0].playerId").value("6743"))
                .andExpect(jsonPath("$.players[0].type").value("skater"))
                .andExpect(jsonPath("$.players[0].reserve").value(false))
                .andExpect(jsonPath("$.players[0].out").value(false))
                .andExpect(jsonPath("$.players[0].restOfSeason.games").value(74.0))
                .andExpect(jsonPath("$.players[0].restOfSeason.stats.points").value(90.0))
                // A defenceman's points count as defencemen's points, as on the wire.
                .andExpect(jsonPath("$.players[0].restOfSeason.stats.defPoints").value(90.0))
                // On injured reserve: takes no roster spot, so dropping him makes no room.
                .andExpect(jsonPath("$.players[1].slot").value("IR+"))
                .andExpect(jsonPath("$.players[1].reserve").value(true))
                .andExpect(jsonPath("$.players[1].out").value(true))
                .andExpect(jsonPath("$.players[1].restOfSeason.games").value(40.0))
                // Day-to-day on the bench is neither reserve nor out.
                .andExpect(jsonPath("$.players[2].type").value("goalie"))
                .andExpect(jsonPath("$.players[2].reserve").value(false))
                .andExpect(jsonPath("$.players[2].out").value(false))
                .andExpect(jsonPath("$.players[2].restOfSeason.stats.gs").value(50.0))
                .andExpect(jsonPath("$.players[2].reserveEligible", hasSize(0)))
                // Out on the bench, and Yahoo says he may go on IR; IR is not a position he plays.
                .andExpect(jsonPath("$.players[3].name").value("Filip Hronek"))
                .andExpect(jsonPath("$.players[3].out").value(true))
                .andExpect(jsonPath("$.players[3].reserve").value(false))
                .andExpect(jsonPath("$.players[3].positions", contains("D")))
                .andExpect(jsonPath("$.players[3].reserveEligible", contains("IR")))
                // Nobody the model knows: listed, without a line.
                .andExpect(jsonPath("$.players[4].name").value("Unknown Prospect"))
                .andExpect(jsonPath("$.players[4].restOfSeason").doesNotExist());
    }

    @Test
    void anEspnPlayerOutOrOnInjuredReserveMayTakeItsIrSlotAndADayToDayOneMayNot() throws Exception {
        var out = espnPlayer(4001L, "Filip Hronek", "OUT");
        var dayToDay = espnPlayer(4002L, "Jake Walman", "DAY_TO_DAY");
        var team = new com.fantasy.bff.generated.espn.model.LeagueRosterTeam();
        team.setTeamId(1);
        team.setName("Slapshots");
        team.setMine(true);
        team.setPlayerIds(List.of(4001L, 4002L));
        team.setPlayers(List.of(out, dayToDay));
        var rosters = new com.fantasy.bff.generated.espn.model.LeagueRostersResponse();
        rosters.setTeams(List.of(team));
        when(espnServiceClient.rosters("user-1", "123")).thenReturn(rosters);

        mockMvc.perform(get("/api/v1/fa-scout/my-team?platform=ESPN&leagueId=123")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].reserveEligible", contains("IR")))
                .andExpect(jsonPath("$.players[1].reserveEligible", hasSize(0)));
    }

    private static com.fantasy.bff.generated.espn.model.LeagueRosterPlayer espnPlayer(
            long id, String name, String injuryStatus) {
        var player = new com.fantasy.bff.generated.espn.model.LeagueRosterPlayer();
        player.setEspnId(id);
        player.setFullName(name);
        player.setTeamAbbrev("Van");
        player.setGoalie(false);
        // ESPN lists its IR slot for every player, so the eligible slots say nothing about it.
        player.setEligiblePositions(List.of("D"));
        player.setLineupSlot("BN");
        player.setInjuryStatus(injuryStatus);
        return player;
    }

    @Test
    void aLeagueWithNoTeamOfTheUsersIsNotFoundAndAsksTheModelNothing() throws Exception {
        LeagueRostersResponse rosters = new LeagueRostersResponse();
        rosters.setLeagueKey("465.l.9");
        rosters.setTeams(List.of(yahooTeam("465.l.9.t.1", "Someone Else", false, List.of())));
        when(yahooServiceClient.rosters("user-1", "465.l.9")).thenReturn(rosters);

        mockMvc.perform(get(YAHOO_LEAGUE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.players", hasSize(0)));
        verifyNoInteractions(projectionServiceClient);
    }

    @Test
    void anOversizedLeagueIdIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/fa-scout/my-team?platform=YAHOO&leagueId=" + "x".repeat(65))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(yahooServiceClient);
    }
}
