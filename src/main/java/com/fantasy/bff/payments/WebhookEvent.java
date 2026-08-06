package com.fantasy.bff.payments;

import java.time.Instant;

public record WebhookEvent(
        String eventId,
        Instant occurredAt,
        WebhookEventType type,
        SubscriptionSnapshot subscription) {
}
