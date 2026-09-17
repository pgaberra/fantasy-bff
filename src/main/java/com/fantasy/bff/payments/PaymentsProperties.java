package com.fantasy.bff.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments")
public record PaymentsProperties(boolean enabled, String provider, Mock mock, Stripe stripe) {

    public PaymentsProperties {
        provider = (provider == null || provider.isBlank()) ? "mock" : provider;
        mock = mock == null ? new Mock(null, null) : mock;
        stripe = stripe == null ? new Stripe(null, null, null, false, 0) : stripe;
    }

    public record Mock(String webhookSecret, String selfBaseUrl) {
        public Mock {
            selfBaseUrl = (selfBaseUrl == null || selfBaseUrl.isBlank()) ? "http://localhost:8080" : selfBaseUrl;
        }
    }

    /**
     * Stripe Billing, sold through Stripe-hosted Checkout. {@code apiKey} and {@code webhookSecret}
     * are secrets and come from the environment; {@code priceId} names the recurring price a
     * checkout subscribes to. Test and live mode are told apart by the key alone ({@code sk_test_}
     * or {@code sk_live_}), so there is no separate host to keep in step with it.
     *
     * <p>{@code managedPayments} makes Stripe the merchant of record for the checkout, which is what
     * keeps VAT and sales tax Stripe's to collect and remit rather than ours. It needs Stripe to have
     * approved the account for Managed Payments, and a checkout asking for it on an account that
     * has not been approved is refused, so it is a switch rather than always on.
     *
     * <p>{@code signatureToleranceSeconds} bounds how old a webhook's timestamp may be before it is
     * refused as a replay; five minutes is also Stripe's own libraries' default.
     */
    public record Stripe(String apiKey, String webhookSecret, String priceId, boolean managedPayments,
                         long signatureToleranceSeconds) {
        public Stripe {
            signatureToleranceSeconds = signatureToleranceSeconds <= 0 ? 300 : signatureToleranceSeconds;
        }
    }
}
