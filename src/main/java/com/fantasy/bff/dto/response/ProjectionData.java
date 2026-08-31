package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.List;

/**
 * A saved projection's contents: how it is scored, the rows, any draft run against it, and the
 * positions its owner corrected by hand.
 */
public record ProjectionData(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid ProjectionSettings settings,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid List<PlayerProjection> players,

        @Valid DraftState draft,

        @Schema(description = "Positions the owner set by hand, replacing what the player read "
                + "model reports for that skater. Absent or empty means every player keeps its "
                + "reported positions.")
        @Valid List<PositionOverride> positionOverrides
) {

    public static ProjectionData from(com.fantasy.bff.generated.db.model.ProjectionData data) {
        if (data == null) {
            return null;
        }
        var overrides = data.getPositionOverrides();
        return new ProjectionData(
                ProjectionSettings.from(data.getProjectionSettings()),
                data.getPlayers().stream().map(PlayerProjection::from).toList(),
                DraftState.from(data.getDraft()),
                overrides == null
                        ? null
                        : overrides.stream().map(PositionOverride::from).toList());
    }
}
