package com.fantasy.bff.config;

import com.fantasy.bff.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The container health check curls {@code /actuator/health}, not a readiness group. So refusing
 * traffic (what {@link DownstreamKeyVerifier} does on a rejected key) only takes an instance out
 * of service if readiness is part of that one endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class ReadinessHealthCheckTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEventPublisher events;

    @Test
    void refusingTrafficTurnsTheHealthCheckRed() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        AvailabilityChangeEvent.publish(events, this, ReadinessState.REFUSING_TRAFFIC);

        mockMvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable());
    }
}
