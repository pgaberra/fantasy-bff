package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The teams in a user's ESPN league, in ESPN's team order. */
public record EspnLeagueTeamsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EspnLeagueTeam> teams
) {
}
