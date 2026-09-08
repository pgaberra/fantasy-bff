package com.fantasy.bff.dto.response;

import com.fantasy.bff.generated.db.model.PremiumGrantResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.OffsetDateTime;

public record AdminPremiumGrantResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "When the grant stops giving premium access") Instant expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Who handed the grant out") String grantedBy,
        @Schema(description = "Free-text note on why premium was given") String reason
) {
    public static AdminPremiumGrantResponse from(PremiumGrantResponse grant, String email) {
        return new AdminPremiumGrantResponse(
                grant.getUserId(),
                email,
                toInstant(grant.getExpiresAt()),
                grant.getGrantedBy(),
                grant.getReason());
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
