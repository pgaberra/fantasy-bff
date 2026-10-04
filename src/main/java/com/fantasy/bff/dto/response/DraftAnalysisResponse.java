package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A league's draft, every pick graded against the model's ranking")
public record DraftAnalysisResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LeagueDraftStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for an auction draft, whose pick order is nomination order: no pick "
                        + "is graded.")
        boolean auction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How the league scores, which is what values are in: fantasy points or "
                        + "summed z-scores.")
        ScoringBasis scoringType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the picks were graded against the model's line frozen on the eve "
                        + "of the season (true), or its current season line where that is not stored.")
        boolean preseason,
        @Schema(description = "The model version the grades came from") String modelVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the per-pick half is filled in. False leaves every pick's rank, "
                        + "value, grade and best available absent; the teams' sums are everyone's.")
        boolean premium,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The teams, best draft first by value added")
        List<DraftAnalysisTeam> teams,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The picks made, in order")
        List<DraftAnalysisPick> picks) {}
