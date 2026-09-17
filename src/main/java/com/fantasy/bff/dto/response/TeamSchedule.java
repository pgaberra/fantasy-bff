package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A team's games over the stretch, scored for streaming")
public record TeamSchedule(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "NHL abbreviation, e.g. TBL") String team,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int games,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int offNightGames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int backToBacks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int homeGames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Each game counts 1, 1.25 on an off-night, scaled by the goals the opponent "
                        + "concedes. Higher is better")
                double skaterScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1 is the best; level teams share a rank")
                int skaterRank,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "As skaterScore, scaled by the inverse of the goals the opponent scores")
                double goalieScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int goalieRank,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ScheduledGame> schedule) {}
