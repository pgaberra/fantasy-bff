package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.generated.yahoo.model.LeaguesResponse;
import com.fantasy.bff.generated.yahoo.model.YahooLeagueProbeResponse;
import com.fantasy.bff.generated.yahoo.model.YahooProbeResponse;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private YahooServiceClient yahooServiceClient;

    @MockitoBean
    private PlayerServiceClient playerServiceClient;

    private String adminToken() {
        return jwtTokenValidator.generateToken("admin-1", "admin@example.com", true);
    }

    private String userToken() {
        return jwtTokenValidator.generateToken("user-1", "user@example.com", false);
    }

    @Test
    void adminCanConnectYahooServiceAccount() throws Exception {
        mockMvc.perform(post("/api/v1/admin/yahoo/connect")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void adminClaimsAYahooLinkForTheServiceAccount() throws Exception {
        mockMvc.perform(post("/api/v1/admin/yahoo/connect/complete")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"code\":\"link-code\"}"))
                .andExpect(status().isOk());

        verify(yahooServiceClient).completeLink("__service__", "link-code");
    }

    @Test
    void nonAdminCannotClaimAYahooLinkForTheServiceAccount() throws Exception {
        mockMvc.perform(post("/api/v1/admin/yahoo/connect/complete")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType("application/json")
                        .content("{\"code\":\"link-code\"}"))
                .andExpect(status().isForbidden());

        verify(yahooServiceClient, never()).completeLink(any(), any());
    }

    @Test
    void adminCanReadConnectionAndTriggerSync() throws Exception {
        mockMvc.perform(get("/api/v1/admin/yahoo/connection")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/player/sync")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/player/sync/runs")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    /**
     * The probe reports a refusal as data, and that has to survive the whole way out — a client
     * that sees a 502 instead of Yahoo's own 403 learns nothing, which is the problem it exists
     * to solve.
     */
    @Test
    void adminCanProbeYahooAccessAndSeeARefusal() throws Exception {
        when(playerServiceClient.probeYahooAccess("nhl", "2026", null, null)).thenReturn(
                new YahooProbeResponse()
                        .ok(false)
                        .path("/game/nhl/players")
                        .status(403)
                        .error("This application is not authorized to perform this action."));

        mockMvc.perform(get("/api/v1/admin/yahoo/probe?gameKey=nhl&season=2026")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error")
                        .value("This application is not authorized to perform this action."));
    }

    /**
     * A league key has to reach the downstream call — it is the input that distinguishes "a game
     * is refused" from "we are refused", which is the question the probe exists to settle.
     */
    @Test
    void passesALeagueKeyThroughToTheProbe() throws Exception {
        when(playerServiceClient.probeYahooAccess("nhl", null, "465.l.12345", null)).thenReturn(
                new YahooProbeResponse().ok(true).path("/league/465.l.12345/players")
                        .status(200).players(25));

        mockMvc.perform(get("/api/v1/admin/yahoo/probe?leagueKey=465.l.12345")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.players").value(25));
    }

    /**
     * The raw league probe reads as the admin themselves, since the service account is in none of
     * the leagues worth asking about. The id must come from the token and nowhere else, or the
     * endpoint would read any member's league for whoever asked.
     */
    @Test
    void readsARawLeagueResourceAsTheSignedInAdmin() throws Exception {
        when(playerServiceClient.probeLeagueResource("477.l.124453", "draft", "admin-1")).thenReturn(
                new YahooLeagueProbeResponse().ok(true)
                        .path("/league/477.l.124453;out=settings,draftresults,teams")
                        .status(200).body("{\"fantasy_content\":{}}"));

        mockMvc.perform(get("/api/v1/admin/yahoo/probe/league?leagueKey=477.l.124453&resource=draft")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.body").value("{\"fantasy_content\":{}}"));
    }

    @Test
    void refusesTheRawLeagueProbeToANonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/yahoo/probe/league?leagueKey=477.l.124453&resource=draft")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    /**
     * The leagues target is the floor question, and it has to reach yahoo-service unchanged: the
     * whole point is to learn what Yahoo says about the most basic call the granted scope covers.
     */
    @Test
    void passesTheLeaguesTargetThroughToTheProbe() throws Exception {
        when(playerServiceClient.probeYahooAccess("nhl", null, null, "leagues")).thenReturn(
                new YahooProbeResponse().ok(false)
                        .path("/users;use_login=1/games;game_keys=nhl/leagues")
                        .status(401).error("Please provide valid credentials"));

        mockMvc.perform(get("/api/v1/admin/yahoo/probe?target=leagues")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Please provide valid credentials"));
    }

    @Test
    void adminCanListTheServiceAccountsLeagues() throws Exception {
        when(yahooServiceClient.leagues("__service__")).thenReturn(new LeaguesResponse());

        mockMvc.perform(get("/api/v1/admin/yahoo/leagues")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    /** Diagnostics are admin-only: the answer names a service account and quotes upstream errors. */
    @Test
    void nonAdminCannotProbe() throws Exception {
        mockMvc.perform(get("/api/v1/admin/yahoo/probe")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/yahoo/connect")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/yahoo/connect"))
                .andExpect(status().isUnauthorized());
    }
}
