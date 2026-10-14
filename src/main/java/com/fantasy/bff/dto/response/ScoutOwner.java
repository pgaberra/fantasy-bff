package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** The league team that holds a player; absent on a row for a player the league has available. */
@Schema(description = "The league team holding a player")
public record ScoutOwner(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The team's name in the league")
                String teamName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True when it is the user's own team")
                boolean mine) {}
