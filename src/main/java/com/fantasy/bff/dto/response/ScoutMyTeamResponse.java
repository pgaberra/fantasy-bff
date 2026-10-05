package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The user's own team in the scouted league, each player with the model's rest of the season. */
@Schema(description = "The user's own team in the chosen league, with the model's rest of the season")
public record ScoutMyTeamResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "False when no team in the league is the user's: a league they only "
                        + "follow, or an ESPN league read without their cookies")
                boolean found,
        @Schema(description = "The team's name; absent when not found") String teamName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Its players today, bench and injured reserve included; empty when "
                        + "not found or before the draft")
                List<ScoutRosterPlayer> players) {}
