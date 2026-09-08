package com.fantasy.bff.dto.response;

import com.fantasy.bff.generated.db.model.PremiumCustomerResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;

public record AdminPremiumCustomerResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(description = "The account's public name; null until the user picks one") String username,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What premium rests on: \"subscription\", \"grant\" or \"both\"") String source,

        @Schema(description = "Subscription status; null when the user has no subscription") String subscriptionStatus,
        @Schema(description = "Payment provider id; null when there is no subscription") String provider,
        @Schema(description = "When the current paid period ends; null when not applicable") Instant currentPeriodEnd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the subscription is set to cancel at period end") boolean cancelAtPeriodEnd,

        @Schema(description = "When the granted premium runs out; null when there is no grant") Instant grantExpiresAt,
        @Schema(description = "Who handed the grant out; null when there is no grant") String grantedBy,
        @Schema(description = "Why premium was given; null when there is no grant or no note") String grantReason,
        @Schema(description = "When premium runs out altogether; null when it is open-ended") Instant premiumUntil,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "When the account was created")
        Instant createdAt
) {
    public static AdminPremiumCustomerResponse from(PremiumCustomerResponse customer) {
        PremiumCustomerResponse.SubscriptionStatusEnum status = customer.getSubscriptionStatus();
        return new AdminPremiumCustomerResponse(
                customer.getUserId(),
                customer.getEmail(),
                customer.getUsername(),
                Objects.toString(customer.getSource(), "none"),
                status == null ? null : status.getValue(),
                customer.getProvider(),
                toInstant(customer.getCurrentPeriodEnd()),
                Boolean.TRUE.equals(customer.getCancelAtPeriodEnd()),
                toInstant(customer.getGrantExpiresAt()),
                customer.getGrantedBy(),
                customer.getGrantReason(),
                toInstant(customer.getPremiumUntil()),
                toInstant(customer.getUserCreatedAt()));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
