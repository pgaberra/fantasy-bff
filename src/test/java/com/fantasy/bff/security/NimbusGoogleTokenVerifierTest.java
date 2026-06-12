package com.fantasy.bff.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class NimbusGoogleTokenVerifierTest {

    @Test
    void verify_withoutConfiguredClientId_throwsIllegalState() {
        NimbusGoogleTokenVerifier verifier = new NimbusGoogleTokenVerifier("");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> verifier.verify("any-token"))
                .withMessageContaining("GOOGLE_CLIENT_ID");
    }
}
