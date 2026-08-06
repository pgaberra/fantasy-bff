package com.fantasy.bff.payments;

import org.springframework.http.HttpHeaders;

/**
 * A payment provider that hosts checkout + a management portal and reports subscription
 * lifecycle changes via signed webhooks. Provider-agnostic on purpose: the BFF depends only
 * on this interface, so a real provider (Stripe, Paddle, …) drops in as another implementation
 * with no change to the controller, the db-service contract, or the web. The in-repo
 * {@link MockPaymentProvider} drives the whole lifecycle locally for test mode.
 */
public interface PaymentProvider {

    /** Stable id stored alongside the subscription (e.g. "mock", "stripe"). */
    String id();

    /** Creates a hosted-checkout session and returns the URL the browser should be sent to. */
    CheckoutSession createCheckoutSession(CheckoutRequest request);

    /** Creates a hosted management-portal session and returns the URL to redirect to. */
    PortalSession createPortalSession(PortalRequest request);

    /**
     * Verifies a webhook's signature over the raw request body and parses it into a normalized
     * {@link WebhookEvent}. Throws {@link IllegalArgumentException} when the signature is invalid.
     */
    WebhookEvent parseAndVerify(byte[] rawBody, HttpHeaders headers);
}
