package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.PlayerServiceClient;
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
