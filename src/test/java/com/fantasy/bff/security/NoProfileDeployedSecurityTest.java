package com.fantasy.bff.security;

import com.fantasy.bff.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks in that a deployed instance is configured correctly with NO active profile — only the
 * env vars Coolify sets. Production used to run the `staging` profile because that profile was
 * the only place CORS origins and the API-docs gate were defined; dropping it would silently
 * have left prod with an empty CORS allowlist. Both now live in the base config, so this test
 * fails if either moves back into a profile.
 */
@SpringBootTest(properties = "WEB_ORIGIN=https://slapstat.com")
@AutoConfigureMockMvc
public class NoProfileDeployedSecurityTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void corsPreflightFromWebOrigin_isAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://slapstat.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://slapstat.com"));
    }

    @Test
    void corsPreflightFromLocalhost_isNotAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void apiDocs_areDisabledWithoutSwaggerEnabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }
}
