package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The teams in a user's ESPN league, in draft order when ESPN gives one, otherwise in ESPN's own
 * team order. The list order alone never says where the user picks — only {@code draftPosition} does.
 */
public record EspnLeagueTeamsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EspnLeagueTeam> teams,
        @Schema(description = "The signed-in manager's own seat in the draft order, counting from 1, "
                + "or null when ESPN does not name it. Null is meaningful: ask the user rather than "
                + "read a seat off the list order.")
        Integer draftPosition
) {
}
