package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.exception.YahooAccessDeniedException;
import com.fantasy.bff.generated.yahoo.model.AuthorizeUrlResponse;
import com.fantasy.bff.generated.yahoo.model.ConnectionResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSummary;
import com.fantasy.bff.generated.yahoo.model.LeaguesResponse;
import com.fantasy.bff.generated.yahoo.model.RosterSlot;
import com.fantasy.bff.generated.yahoo.model.StatCategory;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class YahooControllerIntegrationTest extends BaseIntegrationTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private YahooServiceClient yahooServiceClient;

    private String token() {
        return jwtTokenValidator.generateToken(USER_ID, "owner@example.com");
    }

    @Test
    void connection_forwardsUserIdAndReturnsStatus() throws Exception {
        when(yahooServiceClient.connection(USER_ID))
                .thenReturn(new ConnectionResponse().connected(true));

        mockMvc.perform(get("/api/v1/yahoo/connection").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void connect_returnsAuthorizeUrl() throws Exception {
        when(yahooServiceClient.authorizeUrl(USER_ID))
                .thenReturn(new AuthorizeUrlResponse().authorizeUrl("https://api.login.yahoo.com/oauth2/request_auth?x=1"));

        mockMvc.perform(post("/api/v1/yahoo/connect").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").value("https://api.login.yahoo.com/oauth2/request_auth?x=1"));
    }

    @Test
    void connection_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/yahoo/connection"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void projectionSettings_mapsYahooSettingsToProjectionShape() throws Exception {
        when(yahooServiceClient.settings(USER_ID, "465.l.78677")).thenReturn(
                new LeagueSettingsResponse()
                        .leagueKey("465.l.78677")
                        .name("HHL")
                        .scoringType("head")
                        .statCategories(List.of(new StatCategory().statId(1).name("Goals").displayName("Goals")))
                        .rosterPositions(List.of(new RosterSlot().position("C").count(2))));
        when(yahooServiceClient.leagues(USER_ID)).thenReturn(new LeaguesResponse().leagues(List.of(
                new LeagueSummary().leagueKey("465.l.78677").name("HHL").numTeams(14))));

        mockMvc.perform(get("/api/v1/yahoo/leagues/465.l.78677/projection-settings")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoringType").value("category"))
                .andExpect(jsonPath("$.statWeights").doesNotExist())
                .andExpect(jsonPath("$.leagueSize").value(14))
                .andExpect(jsonPath("$.activeScoringColumns[0]").value("goals"))
                .andExpect(jsonPath("$.rosterSlots.c").value(2));
    }

    /**
     * Not 502, which the web retries and blames on us, and not 403, which the web reads as a
     * Premium refusal: Yahoo's no has to arrive as Yahoo's no, in Yahoo's words.
     */
    @Test
    void leagues_whenYahooRefuses_answers424WithYahoosWording() throws Exception {
        when(yahooServiceClient.leagues(USER_ID)).thenThrow(new YahooAccessDeniedException(
                "Yahoo refused the request: This application is not authorized to perform this action."));

        mockMvc.perform(get("/api/v1/yahoo/leagues").header("Authorization", "Bearer " + token()))
                .andExpect(status().isFailedDependency())
                .andExpect(jsonPath("$.code").value("YAHOO_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value(
                        "Yahoo refused the request: This application is not authorized to perform this action."));
    }
}
