package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Where the Daily Faceoff lineup page listed a player on one day")
public record LineupListing(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate seenOn,
        @Schema(description = "Even-strength group: f1-f4 for forward lines, d1-d3 for pairs; absent when "
                + "listed only on special teams or out of the lineup")
                String line,
        @Schema(description = "1 or 2 for the power-play unit; absent for neither") Integer powerPlayUnit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Listed out of the lineup: injured, scratched or day to day")
                boolean outOfLineup) {}
