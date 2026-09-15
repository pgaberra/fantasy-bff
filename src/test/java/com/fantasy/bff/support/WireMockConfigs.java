package com.fantasy.bff.support;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Plain HTTP/2 (h2c) off: over it WireMock drops POST bodies and journals a request after the
 * client already has its response, so a verify straight after the call fails intermittently.
 */
public final class WireMockConfigs {

    private WireMockConfigs() {
    }

    public static WireMockConfiguration http11() {
        return wireMockConfig().dynamicPort().http2PlainDisabled(true);
    }
}
