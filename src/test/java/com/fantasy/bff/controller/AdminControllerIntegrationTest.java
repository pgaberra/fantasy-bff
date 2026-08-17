package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.PlayerServiceClient;
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
        when(playerServiceClient.probeYahooAccess("nhl", "2026")).thenReturn(
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
