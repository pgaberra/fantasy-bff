package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A skater's usage over a stretch of games: ice a game, power-play ice and his share of "
        + "his club's power play")
public record RoleWindow(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Games he dressed for in the stretch")
                int games,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Seconds of ice a game")
                double toiPerGame,
        @Schema(description = "Seconds of power-play ice a game; absent where no game has the reading")
                Double ppToiPerGame,
        @Schema(description = "His power-play seconds over his club's power-play time in the same games, "
                + "0 to 1; absent where it cannot be read")
                Double ppShare,
        @Schema(description = "Seconds of shorthanded ice a game; absent where no game has the reading")
                Double shToiPerGame) {}
