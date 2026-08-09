package com.fantasy.bff.exception;

import com.fantasy.bff.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A permitted path with nothing mapped to it must answer 404, not 500. Production permits the
 * API-doc URLs while springdoc is disabled, so scanners hitting /v3/api-docs reached the
 * catch-all handler and every probe was logged as an ERROR and alerted on.
 */
@SpringBootTest(properties = "security.permitted-urls=/v3/api-docs/**")
@AutoConfigureMockMvc
public class UnknownPathNotFoundTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void permittedPathWithNoResource_isNotFound() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
