package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The published snapshot: the settings the board was ranked under, and the rows themselves.
 *
 * <p>See {@link SharedPlayer} for why these types are ours rather than db-service's.
 */
public record SharedProjectionData(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ProjectionSettings settings,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SharedPlayer> players
) {}
