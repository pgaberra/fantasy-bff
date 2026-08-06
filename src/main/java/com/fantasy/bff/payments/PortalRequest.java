package com.fantasy.bff.payments;

public record PortalRequest(String userId, String providerCustomerId, String returnUrl) {
}
