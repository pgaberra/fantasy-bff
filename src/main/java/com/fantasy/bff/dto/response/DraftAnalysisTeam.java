package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One team's draft, summed over its picks")
public record DraftAnalysisTeam(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether this is the user's own team.")
        boolean mine,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "How many picks it made.")
        int picks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Its picks' value over their slots, summed: in the league's own unit.")
        double valueAdded,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Picks graded STEAL or GOOD.")
        int goodPicks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Picks graded REACH or BIG_REACH.")
        int badPicks,
        @Schema(description = "A letter from A to F for the whole draft, from its picks' grades. Absent "
                + "where none of its picks could be graded.")
        String grade) {}
