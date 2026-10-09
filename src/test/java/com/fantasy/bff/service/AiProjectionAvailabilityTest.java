package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fantasy.bff.config.AiProjectionProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AiProjectionAvailabilityTest {

    @ParameterizedTest(name = "ai-projection.enabled={0} -> {1}")
    @CsvSource({
        "true,  true",
        "false, false"
    })
    void followsItsSetting(boolean aiProjection, boolean expected) {
        AiProjectionAvailability availability =
                new AiProjectionAvailability(new AiProjectionProperties(aiProjection));

        assertThat(availability.available()).isEqualTo(expected);
    }
}
