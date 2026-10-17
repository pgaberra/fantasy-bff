package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** The nights one NHL club still plays this season. */
@Schema(description = "The dates an NHL club still plays this season")
public record ClubGames(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "NHL abbreviation, e.g. TBL") String team,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Game dates from today on, earliest first")
                List<LocalDate> dates,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The same games with who each is against and where, earliest first")
                List<ClubNight> games) {}
