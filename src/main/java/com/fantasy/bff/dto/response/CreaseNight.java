package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "A goalie's chance of starting one of his club's games")
public record CreaseNight(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "His share of the crease that night, 0 to 1; nought while he is out. "
                        + "A club's goalies' shares of a night add up to its one start.")
                double share) {}
