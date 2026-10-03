package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Whether a draft can be started from the rest of the season right now. */
@Schema(description = "Whether a projection or draft can be seeded with source=rest_of_season right now")
public record RestOfSeasonStatusResponse(
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description =
                                "True while a season is under way and the model has a line for the "
                                        + "rest of it. False before its first game and after its "
                                        + "last, when source=rest_of_season is refused")
                boolean available) {}
