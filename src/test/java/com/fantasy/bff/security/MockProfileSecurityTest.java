package com.fantasy.bff.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
public class MockProfileSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUrl_isUnauthorized() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authUrl_isPermitted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")) // POST /login is permitted but missing body
                .andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    @Test
    void healthUrl_isPermitted() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void corsPreflightFromAnyOrigin_isNotAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
