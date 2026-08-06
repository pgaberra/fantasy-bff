package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.generated.espn.model.CredentialStatusResponse;
import com.fantasy.bff.generated.espn.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.espn.model.LeagueTeam;
import com.fantasy.bff.generated.espn.model.LeagueTeamsResponse;
import com.fantasy.bff.generated.espn.model.RosterSlot;
import com.fantasy.bff.generated.espn.model.StatCategory;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EspnControllerIntegrationTest extends BaseIntegrationTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private EspnServiceClient espnServiceClient;

    private String token() {
        return jwtTokenValidator.generateToken(USER_ID, "owner@example.com");
    }

    @Test
    void credentialStatus_returnsStatus() throws Exception {
        when(espnServiceClient.credentialStatus(USER_ID))
                .thenReturn(new CredentialStatusResponse().hasCredentials(true));

        mockMvc.perform(get("/api/v1/espn/credentials").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCredentials").value(true));
    }

    @Test
    void credentialStatus_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/espn/credentials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void saveCredentials_storesAndReturns204() throws Exception {
        mockMvc.perform(put("/api/v1/espn/credentials")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"espnS2\":\"s2-value\",\"swid\":\"{SWID-1}\"}"))
                .andExpect(status().isNoContent());

        verify(espnServiceClient).saveCredentials(USER_ID, "s2-value", "{SWID-1}");
    }

    @Test
    void saveCredentials_rejectsBlankCookies() throws Exception {
        mockMvc.perform(put("/api/v1/espn/credentials")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"espnS2\":\"\",\"swid\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteCredentials_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/espn/credentials").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNoContent());

        verify(espnServiceClient).deleteCredentials(USER_ID);
    }

    @Test
    void projectionSettings_mapsEspnSettingsToProjectionShape() throws Exception {
        when(espnServiceClient.settings(USER_ID, "123", 2025)).thenReturn(
                new LeagueSettingsResponse()
                        .leagueId("123")
                        .name("HHL")
                        .scoringType("H2H_CATEGORY")
                        .size(14)
                        .statCategories(List.of(new StatCategory().statId(13).name("G")))
                        .rosterPositions(List.of(new RosterSlot().position("C").count(2))));

        mockMvc.perform(get("/api/v1/espn/leagues/123/projection-settings?season=2025")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoringType").value("category"))
                .andExpect(jsonPath("$.statWeights").doesNotExist())
                .andExpect(jsonPath("$.leagueSize").value(14))
                .andExpect(jsonPath("$.activeScoringColumns[0]").value("goals"))
                .andExpect(jsonPath("$.rosterSlots.c").value(2));
    }

    @Test
    void teams_returnsMappedTeamsWithMineFlag() throws Exception {
        when(espnServiceClient.teams(eq(USER_ID), eq("123"), any(Integer.class))).thenReturn(
                new LeagueTeamsResponse().teams(List.of(
                        new LeagueTeam().name("Alpha").mine(false),
                        new LeagueTeam().name("Beta Squad").mine(true))));

        mockMvc.perform(get("/api/v1/espn/leagues/123/teams?season=2025")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].name").value("Alpha"))
                .andExpect(jsonPath("$.teams[1].name").value("Beta Squad"))
                .andExpect(jsonPath("$.teams[1].mine").value(true));
    }
}
