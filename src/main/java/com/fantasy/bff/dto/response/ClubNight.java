package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** One of a club's remaining games: the night, who it is against and where. */
@Schema(description = "One of an NHL club's remaining games: the date, the opponent and the venue")
public record ClubNight(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
        @Schema(description = "The opponent's NHL abbreviation; absent where the schedule names none") String opponent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The club plays at home") boolean home) {}
