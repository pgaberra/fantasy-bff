package com.fantasy.bff.payments;

/**
 * What a provider needs to open a checkout for one account.
 *
 * @param customerEmail the account's email when it has been verified, otherwise {@code null}.
 *                      Only a verified address may name the buyer at the provider: an unverified
 *                      one could belong to someone else, and attaching that person's customer
 *                      record to this account's purchase would open their billing portal to it.
 */
public record CheckoutRequest(String userId, String successUrl, String cancelUrl, String customerEmail) {
}
