package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.generated.yahoo.model.AuthorizeUrlResponse;
import com.fantasy.bff.generated.yahoo.model.ConnectionResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
}
