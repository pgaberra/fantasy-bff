package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A player on the user's own team, with the model's rest of the season for him on the same scale as
 * the available players' lines: what the client weighs against a pickup to find who to drop.
 */
@Schema(description = "A player on the user's own team and his rest of the season")
public record ScoutRosterPlayer(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The platform's player id")
                String playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "His name; the platform's id when the platform sent none")
                String name,
        @Schema(description = "The club he is on, in the platform's own spelling; absent for none")
                String teamAbbrev,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "skater or goalie")
                String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every position the league may start him at (C, LW, RW, D, G); empty "
                        + "when the platform does not say")
                List<String> positions,
        @Schema(description = "The slot he sits in today, in the platform's spelling (C, Util, BN, "
                + "IR, IR+, NA, ...); absent when the platform does not say")
                String slot,
        @Schema(description = "The platform's injury status (DTD, O, IR, DAY_TO_DAY, OUT, ...); absent "
                + "for a healthy player")
                String injuryStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True when he sits on injured reserve or not-active (IR, IR+, IR-LT, "
                        + "NA): a slot that takes no roster spot, so dropping him makes no room")
                boolean reserve,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True when he fills no lineup slot now: on reserve, or out with an "
                        + "injury or suspension. A day-to-day player is not out.")
                boolean out,
        @Schema(description = "The model's rest of the season, as the available players' lines are "
                + "given; absent when the model has no line for him")
                ScoutLine restOfSeason) {}
