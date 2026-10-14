package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The user's own team in the scouted league with the model's rest of the season for each player,
 * and every club's remaining nights, so the client can seat the roster night by night over the rest
 * of the season and say what each player's place in the lineup is worth.
 */
@Schema(description = "The user's own team with the model's rest of the season, and the clubs' remaining nights")
public record PlayerScoutTeamResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "False when no team in the league is the user's: a league they only "
                        + "follow, or an ESPN league read without their cookies")
                boolean found,
        @Schema(description = "The team's name; absent when not found") String teamName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Its players today, bench and injured reserve included; empty when "
                        + "not found or before the draft")
                List<ScoutRosterPlayer> players,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every NHL club's game dates from today to the end of the season, in the "
                        + "player pool's spelling of the club")
                List<ClubGames> schedule) {}
