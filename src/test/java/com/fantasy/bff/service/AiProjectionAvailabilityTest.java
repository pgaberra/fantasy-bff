package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fantasy.bff.config.AiProjectionProperties;
import com.fantasy.bff.config.SecurityProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AiProjectionAvailabilityTest {

    /**
     * The seed endpoint sits behind both settings, so the answer does too: an environment that
     * closed the model prefix must not report, or serve, the model through another door.
     */
    @ParameterizedTest(name = "ai-projection.enabled={0}, projection-model-enabled={1} -> {2}")
    @CsvSource({
        "true,  true,  true",
        "true,  false, false",
        "false, true,  false",
        "false, false, false"
    })
    void isAvailableOnlyWhenBothSettingsAllowIt(boolean aiProjection, boolean modelPrefix, boolean expected) {
        AiProjectionAvailability availability = new AiProjectionAvailability(
                new AiProjectionProperties(aiProjection),
                new SecurityProperties(null, null, null, modelPrefix));

        assertThat(availability.available()).isEqualTo(expected);
    }
}
