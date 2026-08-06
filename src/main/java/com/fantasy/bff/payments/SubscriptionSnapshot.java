package com.fantasy.bff.payments;

import java.time.Instant;

public record SubscriptionSnapshot(
        String userId,
        String providerCustomerId,
        String providerSubscriptionId,
        String priceId,
        String status,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd) {
}
