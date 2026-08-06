package com.fantasy.bff.payments;

public record CheckoutRequest(String userId, String successUrl, String cancelUrl) {
}
