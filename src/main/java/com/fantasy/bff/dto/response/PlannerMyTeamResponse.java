package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The user's own team in a league, today, for the planner to find the nights it has room. */
@Schema(description = "The user's own team in the chosen league")
public record PlannerMyTeamResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "False when no team in the league is the user's: a league they only "
                        + "follow, or an ESPN league read without their cookies")
                boolean found,
        @Schema(description = "The team's name; absent when not found") String teamName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Its players today, bench and injured reserve included; empty when "
                        + "not found or before the draft")
                List<PlannerRosterPlayer> players) {}
