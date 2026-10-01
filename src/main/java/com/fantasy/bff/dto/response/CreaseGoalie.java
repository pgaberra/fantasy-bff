package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One goalie of a club's crease: his chance of starting each of its games")
public record CreaseGoalie(
        @Schema(description = "The platform's player id, when he is one of the players listed; "
                + "absent for a goalie the league has rostered")
                String playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Each of the club's games in the stretch, in date order")
                List<CreaseNight> nights) {}
