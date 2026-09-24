package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The rows a projection created from the model starts with, before it is created: the model's
 * lines, plus last season's line for every player in the pool the model does not reach.
 */
@Schema(description = "The rows a projection created with source=model would be written with")
public record ModelBoardResponse(
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description =
                                "Every row, keyed by the platform's player id: the model's line where "
                                        + "it has one, last season's otherwise")
                List<PlayerProjection> players) {}
