package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "A Monday-Sunday week of the NHL season")
public record PlannerWeek(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1 is the week of opening night")
                int week,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Opening night in week 1, else the Monday")
                LocalDate start,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The Sunday, or the last game in the final week")
                LocalDate end,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Games that week; 0 in a break")
                int games) {}
