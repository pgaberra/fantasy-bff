package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VersionControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getVersions_isPublic_andReportsBffVersion() throws Exception {
        mockMvc.perform(get("/api/v1/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].name").value("fantasy-bff"))
                .andExpect(jsonPath("$.services[0].up").value(true))
                .andExpect(jsonPath("$.services[0].version").value("dev"))
                .andExpect(jsonPath("$.services.length()").value(5))
                .andExpect(jsonPath("$.services[3].name").value("fantasy-espn-service"))
                .andExpect(jsonPath("$.services[4].name").value("fantasy-projection-service"))
                // The wiring, not the setting: it says which source actually answered.
                .andExpect(jsonPath("$.playerSource").value("yahoo"));
    }
}
