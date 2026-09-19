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

        @Schema(description = "Who this board was copied from, on a projection imported from a "
                + "share link. Absent on the user's own.")
        ProjectionOrigin origin,

        @Schema(description = "The board a draft was started from. Absent on anything that is not "
                + "a draft, on a draft started from a preset, and once that board is deleted — a "
                + "draft holds its own copy of the numbers and outlives it.")
        String sourceProjectionId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the name is still the one the server gave this row. False "
                        + "once its owner has renamed it, which a league sync then leaves alone.")
        boolean autoNamed
) {

    /**
     * @param shareToken     the link the board was copied from, which may since have gone
     * @param authorUsername the author's name as it read when the copy was taken
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
                originOf(stored.getOrigin()),
                stored.getSourceProjectionId(),
                Boolean.TRUE.equals(stored.getAutoNamed()));
    }

    private static ProjectionKind kindOf(
            com.fantasy.bff.generated.db.model.ProjectionResponse.KindEnum kind) {
        if (kind == null) {
            return ProjectionKind.PROJECTION;
        }
        return switch (kind) {
            case DRAFT -> ProjectionKind.DRAFT;
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
