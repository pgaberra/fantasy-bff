package com.fantasy.bff.payments;

/**
 * A checkout opened with the provider.
 *
 * @param url       where the browser pays it
 * @param reference the provider's own id for it (a Stripe checkout session id), so a later checkout for
 *                  the same account can ask whether this one can still be paid and reuse it
 */
public record CheckoutSession(String url, String reference) {
}
