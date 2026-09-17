package com.fantasy.bff.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments")
public record PaymentsProperties(boolean enabled, String provider, Mock mock, Paddle paddle, Stripe stripe) {

    public PaymentsProperties {
        provider = (provider == null || provider.isBlank()) ? "mock" : provider;
        mock = mock == null ? new Mock(null, null) : mock;
        paddle = paddle == null ? new Paddle(null, null, null, null, 0) : paddle;
        stripe = stripe == null ? new Stripe(null, null, null, false, 0) : stripe;
    }

    public record Mock(String webhookSecret, String selfBaseUrl) {
        public Mock {
            selfBaseUrl = (selfBaseUrl == null || selfBaseUrl.isBlank()) ? "http://localhost:8080" : selfBaseUrl;
        }
    }

    /**
     * Paddle Billing. {@code apiKey} and {@code webhookSecret} are secrets and come from the
     * environment; {@code priceId} names the recurring price a checkout subscribes to, and
     * {@code checkoutUrl} is the page on our own site that hosts Paddle's checkout — blank
     * falls back to the default payment link configured in Paddle's dashboard.
     *
     * <p>{@code signatureToleranceSeconds} bounds how old a webhook's own timestamp may be
     * before we refuse it as a replay. Paddle's SDKs default to five seconds, which is tight
     * enough that ordinary clock drift between their servers and ours rejects genuine events;
     * a wider window still defeats replay while surviving a second or two of skew.
     */
    public record Paddle(String apiKey, String webhookSecret, String priceId, String checkoutUrl,
                         long signatureToleranceSeconds) {
        public Paddle {
            signatureToleranceSeconds = signatureToleranceSeconds <= 0 ? 300 : signatureToleranceSeconds;
        }
    }

    /**
     * Stripe Billing, sold through Stripe-hosted Checkout. {@code apiKey} and {@code webhookSecret}
     * are secrets and come from the environment; {@code priceId} names the recurring price a
     * checkout subscribes to. Test and live mode are told apart by the key alone ({@code sk_test_}
     * or {@code sk_live_}), so unlike Paddle there is no separate host to keep in step with it.
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
