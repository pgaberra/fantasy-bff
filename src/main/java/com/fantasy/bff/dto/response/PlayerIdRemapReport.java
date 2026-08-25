package com.fantasy.bff.dto.response;

import com.fantasy.bff.generated.db.model.PlayerIdRemapResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * What matching Yahoo's players to ESPN's produced, and what db-service did with it. Both
 * halves are here because the second is only worth reading in the light of the first: a low
 * coverage means the rows that went unmapped are stranded, not that the write failed.
 */
public record PlayerIdRemapReport(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Players in the Yahoo pool")
        int yahooPlayers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Players in the ESPN pool")
        int espnPlayers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yahoo players that found an ESPN counterpart")
        int matched,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Matched on the full name")
        int matchedOnName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Matched on last name plus first initial, where that form was unique on both sides")
        int matchedOnFallback,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unmatched,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Share of the whole Yahoo pool that found a counterpart. Read the "
                        + "played coverage below instead: the pool is a frozen snapshot of "
                        + "everyone who was fantasy-relevant last season, hundreds of whom never "
                        + "got into a game and are simply not on the new platform's active list.")
        double coverage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yahoo players who played at least one game in the reference season")
        int playersWithGames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How many of those found an ESPN counterpart")
        int matchedWithGames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Share of the players who actually played that found a counterpart. "
                        + "This is what the remap is gated on — they are the ones a projection "
                        + "has real numbers for.")
        double coverageOfPlayersWithGames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yahoo players with no ESPN counterpart, busiest first and with "
                        + "the games they played, at most 50 of them. A platform only lists "
                        + "players with fantasy relevance, so a long tail of 0 GP here is "
                        + "expected; anyone with a real season is how a broken match is noticed.")
        List<String> unmatchedSample,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What db-service did with the crosswalk, or would have done on a dry run")
        PlayerIdRemapResponse applied
) {
}
