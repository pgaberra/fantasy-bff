package com.fantasy.bff.dto.response;

import com.fantasy.bff.dto.request.ProjectionKind;
import com.fantasy.bff.generated.db.model.ProjectionData;
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

        @Schema(description = "How many player rows this read had to add and drop to match the "
                + "current player pool. Present only when the pool had moved since these rows "
                + "were last squared with it, so a client can say so once and then stop.")
        PoolReconciliation poolReconciliation
) {

    /**
     * @param added rows added for players who joined the pool, seeded from the projection's basis
     * @param removed rows dropped for players the pool no longer carries
     */
    public record PoolReconciliation(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int added,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int removed
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
                stored.getData(),
                stored.getCreatedAt(),
                stored.getUpdatedAt(),
                poolReconciliation);
    }

    private static ProjectionKind kindOf(
            com.fantasy.bff.generated.db.model.ProjectionResponse.KindEnum kind) {
        return kind == com.fantasy.bff.generated.db.model.ProjectionResponse.KindEnum.PRESET_DRAFT
                ? ProjectionKind.PRESET_DRAFT
                : ProjectionKind.PROJECTION;
    }
}
