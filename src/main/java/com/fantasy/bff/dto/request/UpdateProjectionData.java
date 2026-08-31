package com.fantasy.bff.dto.request;

import com.fantasy.bff.dto.response.DraftState;
import com.fantasy.bff.dto.response.PlayerProjection;
import com.fantasy.bff.dto.response.PositionOverride;
import com.fantasy.bff.dto.response.ProjectionSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * What a save carries. Only the settings are required: an autosave that changed nothing but the
 * scoring has no reason to send the whole board back, and a projection with no draft against it
 * has no draft to send.
 */
public record UpdateProjectionData(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid ProjectionSettings settings,

        @Valid List<PlayerProjection> players,

        @Valid DraftState draft,

        @Schema(description = "Positions the owner set by hand. Omit to keep the stored ones, so "
                + "a save that has nothing to do with positions cannot clear them. Send an empty "
                + "list to put every player back on the positions the read model reports.")
        @Valid List<PositionOverride> positionOverrides
) {

    public com.fantasy.bff.generated.db.model.UpdateProjectionData toDownstream() {
        return new com.fantasy.bff.generated.db.model.UpdateProjectionData()
                .projectionSettings(settings.toDownstream())
                .players(players == null
                        ? null
                        : players.stream().map(PlayerProjection::toDownstream).toList())
                .draft(draft == null ? null : draft.toDownstream())
                .positionOverrides(positionOverrides == null
                        ? null
                        : positionOverrides.stream().map(PositionOverride::toDownstream).toList());
    }
}
