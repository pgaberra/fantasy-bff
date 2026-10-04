package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A player the league has available, with the model's rest of the season for him and the line it
 * gave him before the season began. The client scores both with the league's own settings: how far
 * the first has moved past the second is what makes a player worth keeping who went undrafted.
 */
@Schema(description = "An available player, his rest of the season and his preseason line")
public record ScoutPlayerResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The platform's player id")
                String playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "The club he is on, in the platform's own spelling") String teamAbbrev,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "skater or goalie")
                String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every position the league may start him at")
                List<String> positions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "FREE_AGENT to add now, WAIVERS to claim first, UNKNOWN when the "
                        + "platform does not say")
                PlayerAvailability availability,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The model's rest of the season, lifted to the season line's scale, "
                        + "with the season so far left out")
                ScoutLine restOfSeason,
        @Schema(description = "The model's line for the whole season as it stood on the eve of it, "
                        + "or absent when the model had none for him then")
                ScoutLine preseason) {}
