package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Which earlier line the players' {@code then} lines are: the day the client asked for, and what
 * answered it. The model's lines are kept day by day only from the day the history began, and the
 * preseason line before that, so a day nothing was kept for is answered with the newest line
 * before it, and the client says so.
 */
@Schema(description = "The earlier day the players' then-lines stand for, and what answered it")
public record ScoutSince(
        @Schema(description = "The day the client asked for; absent for the preseason line") LocalDate requested,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "SNAPSHOT when a day's kept line answered, PRESEASON when the model's line "
                        + "from the eve of the season did (asked for, or the newest before the day "
                        + "asked for), NONE when neither is stored and no player has a then-line",
                allowableValues = {"SNAPSHOT", "PRESEASON", "NONE"})
                String kind,
        @Schema(description = "The day the kept line was counted from, for SNAPSHOT; absent otherwise")
                LocalDate resolved,
        @Schema(description = "The first day a line is kept for this season; absent while none is")
                LocalDate earliestSnapshot) {}
