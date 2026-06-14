package com.fantasy.bff.security;

import com.fantasy.bff.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("staging")
public class StagingProfileSecurityTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void versionsUrl_isPermitted() throws Exception {
        mockMvc.perform(get("/api/v1/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].name").value("fantasy-bff"));
    }

    @Test
    void swaggerUrl_isPermitted() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
