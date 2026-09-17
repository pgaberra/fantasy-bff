package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "A date in the stretch and how busy it is")
public record ScheduleNight(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int games,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean offNight) {}
