package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "The players a league has available over a stretch, best projected first")
public record FreeAgentListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate start,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate end,
        @Schema(description = "Season the projections are for") Integer season,
        @Schema(description = "Model version behind them") String modelVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FreeAgentResponse> players,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Available players the model has no projection for. Mostly fringe "
                        + "players and call-ups; they are left out rather than shown with zeroes.")
                int unprojected) {}
