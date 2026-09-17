package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "The season's weeks the streamer planner can show")
public record PlannerWeeksResponse(
        @Schema(description = "The year the season starts in. Absent when no schedule is published") Integer season,
        @Schema(description = "The week today falls in: the first before opening night, the last after the season. "
                        + "Absent when there are no weeks")
                Integer currentWeek,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlannerWeek> weeks) {}
