package com.fantasy.bff.dto.request;

import com.fantasy.bff.dto.response.DraftState;
import com.fantasy.bff.dto.response.PlayerProjection;
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

        @Valid DraftState draft
) {

    public com.fantasy.bff.generated.db.model.UpdateProjectionData toDownstream() {
        return new com.fantasy.bff.generated.db.model.UpdateProjectionData()
                .projectionSettings(settings.toDownstream())
                .players(players == null
                        ? null
                        : players.stream().map(PlayerProjection::toDownstream).toList())
                .draft(draft == null ? null : draft.toDownstream());
    }
}
