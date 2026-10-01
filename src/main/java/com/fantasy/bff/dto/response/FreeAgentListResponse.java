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
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The available players the model projects. One it does not is "
                        + "left out rather than shown with zeroes.")
                List<FreeAgentResponse> players,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The crease of every club with a goalie among the players, so a client "
                        + "counting only some of the nights can give each game to the goalie "
                        + "likeliest to start it rather than a fraction of it to each")
                List<PlannerCrease> creases) {}
