package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "The seasons Who's hot can measure, newest first")
public record SplitSeasonListResponse(
        @Schema(description = "The season a split with no season reads: the newest with a game played. "
                        + "Absent when no season has one")
                Integer defaultSeason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SplitSeason> seasons) {}
