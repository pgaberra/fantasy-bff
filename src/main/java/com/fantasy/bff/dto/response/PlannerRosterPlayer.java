package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A player on the user's own team, as the planner needs him to fill the league's lineup night by
 * night: who he is, which club's schedule he follows, where he may be started and whether he is
 * out of the lineup altogether.
 */
@Schema(description = "A player on the user's own team in the chosen league")
public record PlannerRosterPlayer(
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
                description = "True when he fills no lineup slot this stretch: parked on injured "
                        + "reserve or not active (IR, IR+, IR-LT, NA), or out with an injury or "
                        + "suspension. A day-to-day player is not out.")
                boolean out) {}
