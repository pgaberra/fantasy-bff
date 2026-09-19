package com.fantasy.bff.dto.response;

import com.fantasy.bff.dto.request.ProjectionKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * A saved projection as the app reads it, mirroring what db-service stores. The players a
 * reconciliation with the pool added travel in the settings, as
 * {@code unacknowledgedNewPlayerIds}, rather than beside them: the notice about them has to
 * outlast the one read that added them.
 */
public record ProjectionResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProjectionKind kind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The season this projection is for, as its 8-digit code.")
        String season,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProjectionData data,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt,

        @Schema(description = "The share link this board follows, and who published it. Present "
                + "only on a follow, which is read-only apart from its draft, and never stale: "
                + "the follow goes when the share does. Absent on the user's own boards, "
                + "including a copy taken from a link.")
        ProjectionOrigin origin
) {

    /**
     * @param shareToken     the link this board follows
     * @param authorUsername the author's name as it read when the link was first followed
     */
    public record ProjectionOrigin(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String shareToken,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String authorUsername
    ) {}

    public static ProjectionResponse of(com.fantasy.bff.generated.db.model.ProjectionResponse stored) {
        return new ProjectionResponse(
                stored.getId(),
                stored.getName(),
                kindOf(stored.getKind()),
                stored.getSeason().getValue(),
                ProjectionData.from(stored.getData()),
                stored.getCreatedAt(),
                stored.getUpdatedAt(),
                originOf(stored.getOrigin()));
    }

    private static ProjectionKind kindOf(
            com.fantasy.bff.generated.db.model.ProjectionResponse.KindEnum kind) {
        if (kind == null) {
            return ProjectionKind.PROJECTION;
        }
        return switch (kind) {
            case PRESET_DRAFT -> ProjectionKind.PRESET_DRAFT;
            case IMPORTED -> ProjectionKind.IMPORTED;
            case PROJECTION -> ProjectionKind.PROJECTION;
        };
    }

    private static ProjectionOrigin originOf(
            com.fantasy.bff.generated.db.model.ProjectionOrigin origin) {
        return origin == null
                ? null
                : new ProjectionOrigin(origin.getShareToken(), origin.getAuthorUsername());
    }
}
