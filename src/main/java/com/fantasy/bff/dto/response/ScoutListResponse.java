package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A league's available players with the model's rest of the season")
public record ScoutListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int season,
        @Schema(description = "The model version the rest of the season came from") String modelVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the season is under way. False before its first game and after "
                        + "its last, when there is no rest of the season and no player is listed.")
                boolean inSeason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the model's preseason line is stored for this season. False "
                        + "leaves every player's preseason absent, so nobody can read as rising.")
                boolean preseasonAvailable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Available players the model projects, in no ranked order: score them "
                        + "with the league's settings")
                List<ScoutPlayerResponse> players) {}
