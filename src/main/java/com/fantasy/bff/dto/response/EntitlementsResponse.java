package com.fantasy.bff.dto.response;

import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;

public record EntitlementsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the user currently has premium access") boolean premium,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Subscription status code, or \"none\" when there is no subscription") String status,
        @Schema(description = "When the current paid period ends; null when not applicable") Instant currentPeriodEnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the subscription is set to cancel at period end") boolean cancelAtPeriodEnd
) {
    public static EntitlementsResponse none() {
        return new EntitlementsResponse(false, "none", null, false);
    }

    public static EntitlementsResponse from(SubscriptionResponse subscription) {
        OffsetDateTime periodEnd = subscription.getCurrentPeriodEnd();
        return new EntitlementsResponse(
                Boolean.TRUE.equals(subscription.getPremium()),
                Objects.toString(subscription.getStatus(), "none"),
                periodEnd == null ? null : periodEnd.toInstant(),
                Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
    }
}
