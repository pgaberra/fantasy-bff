package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the ice says about a skater since the chosen day: his measured usage over the games since
 * it beside his usage before it, and where the lineup page lists him now and listed him then.
 * Measured, not projected, and never scored: the client reads the change.
 */
@Schema(description = "A skater's measured ice and power-play role since the chosen day, beside before it")
public record ScoutUsage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "His games on or after the chosen day")
                RoleWindow recent,
        @Schema(description = "His games before it this season, or his whole last season while this "
                + "season has too few; absent for a call-up with neither")
                RoleWindow baseline,
        @Schema(description = "SEASON for his earlier games this season, LAST_SEASON for last season",
                allowableValues = {"SEASON", "LAST_SEASON"})
                String baselineSource,
        @Schema(description = "Where the Daily Faceoff lineup page lists him now") LineupListing listedNow,
        @Schema(description = "Where the page listed him the day before the chosen day")
                LineupListing listedBefore) {}
