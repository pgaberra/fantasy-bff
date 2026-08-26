package com.fantasy.bff.dto.response;

import com.fantasy.bff.dto.request.ProjectionKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * A saved projection as the app reads it. This mirrors what db-service stores and adds one thing
 * db-service cannot know: whether serving this read had to square the player rows with the
 * current player pool, and by how much. Only a read can report that — a create or an update
 * leaves {@code poolReconciliation} out.
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

        @Schema(description = "How many player rows this read added to match the current player "
                + "pool. Present only when the pool had moved since these rows were last squared "
                + "with it, so a client can say so once and then stop. Nothing is removed by a "
                + "reconciliation — a row whose player has left the pool stays and is not shown.")
        PoolReconciliation poolReconciliation
) {

    /**
     * @param shareToken     the link the board was copied from, which may since have gone
     * @param authorUsername the author's name as it read when the copy was taken
     */
    public record ProjectionOrigin(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String shareToken,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String authorUsername
    ) {}

    /**
     * @param added rows added for players who joined the pool, seeded from the projection's basis
     */
    public record PoolReconciliation(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int added
    ) {}

    public static ProjectionResponse of(com.fantasy.bff.generated.db.model.ProjectionResponse stored) {
        return of(stored, null);
    }

    public static ProjectionResponse of(com.fantasy.bff.generated.db.model.ProjectionResponse stored,
                                        PoolReconciliation poolReconciliation) {
        return new ProjectionResponse(
                stored.getId(),
                stored.getName(),
                kindOf(stored.getKind()),
                stored.getSeason().getValue(),
                ProjectionData.from(stored.getData()),
                stored.getCreatedAt(),
                stored.getUpdatedAt(),
                originOf(stored.getOrigin()),
                poolReconciliation);
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
