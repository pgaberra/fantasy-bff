package com.fantasy.bff.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments")
public record PaymentsProperties(boolean enabled, String provider, Mock mock) {

    public PaymentsProperties {
        provider = (provider == null || provider.isBlank()) ? "mock" : provider;
        mock = mock == null ? new Mock(null, null) : mock;
    }

    public record Mock(String webhookSecret, String selfBaseUrl) {
        public Mock {
            selfBaseUrl = (selfBaseUrl == null || selfBaseUrl.isBlank()) ? "http://localhost:8080" : selfBaseUrl;
        }
    }
}
