package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "payments.enabled=false")
class BillingControllerDisabledTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private DatabaseServiceClient databaseServiceClient;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private String token() {
        return jwtTokenValidator.generateToken(USER_ID.toString(), "owner@example.com");
    }

    @Test
    void checkoutSession_whenDisabled_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/billing/checkout-session").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void entitlements_whenDisabled_returnPremiumFalse() throws Exception {
        mockMvc.perform(get("/api/v1/billing/entitlements").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.status").value("none"));
    }

    @Test
    void mockStubPages_whenDisabled_return404() throws Exception {
        mockMvc.perform(get("/api/v1/billing/mock/checkout")).andExpect(status().isNotFound());
    }
}
