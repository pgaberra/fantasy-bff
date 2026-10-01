package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RosteredPlayersResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every player on a team's roster in the league today, bench and injured "
                        + "reserve included, by the player pool's id. A player the pool has no "
                        + "counterpart for is left out: no row on a board can be him.")
        List<Integer> playerIds
) {}
