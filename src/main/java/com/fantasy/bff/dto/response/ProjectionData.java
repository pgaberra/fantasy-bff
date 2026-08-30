package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.List;

/** A saved projection's contents: how it is scored, the rows, and any draft run against it. */
public record ProjectionData(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid ProjectionSettings settings,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid List<PlayerProjection> players,

        @Valid DraftState draft
) {

    public static ProjectionData from(com.fantasy.bff.generated.db.model.ProjectionData data) {
        if (data == null) {
            return null;
        }
        return new ProjectionData(
                ProjectionSettings.from(data.getProjectionSettings()),
                data.getPlayers().stream().map(PlayerProjection::from).toList(),
                DraftState.from(data.getDraft()));
    }
}
