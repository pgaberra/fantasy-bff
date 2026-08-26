package com.fantasy.bff.dto.response;

import com.fantasy.bff.generated.db.model.ProjectionSettings;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The published snapshot: the settings the board was ranked under, and the rows themselves.
 *
 * <p>{@code settings} is still db-service's {@link ProjectionSettings}. It is the root of the
 * projection endpoints' own object graph, so it moves to a type of ours with those rather than
 * being pulled up here on its own — see {@link SharedPlayer} for why any of this is ours at all.
 */
public record SharedProjectionData(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ProjectionSettings settings,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SharedPlayer> players
) {}
