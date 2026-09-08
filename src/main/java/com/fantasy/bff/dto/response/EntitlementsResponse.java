package com.fantasy.bff.dto.response;

import com.fantasy.bff.generated.db.model.PremiumEntitlementResponse;
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
                description = "Whether the subscription is set to cancel at period end") boolean cancelAtPeriodEnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What premium rests on: \"none\", \"subscription\", \"grant\" or \"both\". "
                        + "A grant is premium given by hand, with nothing to bill or manage")
        String source,
        @Schema(description = "When premium runs out altogether; null when it is open-ended or absent")
        Instant premiumUntil
) {
    public static EntitlementsResponse none() {
        return new EntitlementsResponse(false, "none", null, false, "none", null);
    }

    public static EntitlementsResponse from(PremiumEntitlementResponse entitlement) {
        return new EntitlementsResponse(
                Boolean.TRUE.equals(entitlement.getPremium()),
                Objects.toString(entitlement.getSubscriptionStatus(), "none"),
                toInstant(entitlement.getCurrentPeriodEnd()),
                Boolean.TRUE.equals(entitlement.getCancelAtPeriodEnd()),
                Objects.toString(entitlement.getSource(), "none"),
                toInstant(entitlement.getPremiumUntil()));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
