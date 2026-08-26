package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A shared projection as a visitor reads it. This mirrors the snapshot db-service stores and adds
 * the one thing db-service cannot know: who is asking, and therefore how much of the board they
 * get. A signed-in visitor reads all of it; anyone else gets the top rows and is told what they
 * are missing.
 *
 * <p>The cut is made here rather than in the browser on purpose. A page that fetched the whole
 * board and rendered a slice of it would be a gate anyone could open with the network tab — the
 * rows an anonymous visitor may not see never leave the BFF.
 */
public record SharedProjectionResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The owner's public name. Read live rather than snapshotted: a "
                        + "rename should follow onto links already shared.")
        String authorUsername,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The season this projection is for, as its 8-digit code.")
        String season,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The snapshot. Its rows are the whole published board for a "
                        + "signed-in reader, and the top of it otherwise.")
        SharedProjectionData data,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How many rows the published board has in total, whether or not "
                        + "this response carries them all.")
        int totalPlayers,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True when rows were withheld because the reader is not signed in.")
        boolean truncated,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt
) {

    /** Every row the owner published. */
    public static SharedProjectionResponse full(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared) {
        List<com.fantasy.bff.generated.db.model.SharedPlayer> players = shared.getData().getPlayers();
        return of(shared, players, players.size(), false);
    }

    /** The top {@code rows} of the board, for a reader who is not signed in. */
    public static SharedProjectionResponse preview(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared, int rows) {
        List<com.fantasy.bff.generated.db.model.SharedPlayer> players = shared.getData().getPlayers();
        int visible = Math.min(rows, players.size());
        return of(shared, players.subList(0, visible), players.size(), visible < players.size());
    }

    private static SharedProjectionResponse of(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared,
            List<com.fantasy.bff.generated.db.model.SharedPlayer> players,
            int totalPlayers, boolean truncated) {
        return new SharedProjectionResponse(
                shared.getToken(),
                shared.getName(),
                shared.getAuthorUsername(),
                shared.getSeason().getValue(),
                new SharedProjectionData(
                        shared.getData().getSettings(),
                        players.stream().map(SharedPlayer::from).toList()),
                totalPlayers,
                truncated,
                shared.getCreatedAt(),
                shared.getUpdatedAt());
    }
}
