package com.fantasy.bff.dto.response;

import com.fantasy.bff.service.SharedBoardFilters;
import com.fantasy.bff.service.SharedBoardPreview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every team with a player on the published board, alphabetically. "
                        + "Read off the whole board rather than the rows in this response, so "
                        + "the team filter offers the same choices to every reader.")
        List<String> teams,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Which players on the published board are rookies. Rookie status "
                        + "is not part of the snapshot, so it is read live: empty means nobody "
                        + "is, or that it could not be determined, and either way the page "
                        + "offers no rookie filter.")
        List<Integer> rookieIds,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt
) {

    /**
     * Every row the owner published.
     *
     * @param headshots whether this environment shows players' pictures; without them each row
     *     goes out with no headshot, whatever address the snapshot stored
     */
    public static SharedProjectionResponse full(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared,
            Set<Integer> rookieIds, boolean headshots) {
        List<com.fantasy.bff.generated.db.model.SharedPlayer> players = shared.getData().getPlayers();
        return of(shared, players, players.size(), false, rookieIds, headshots);
    }

    /**
     * The top {@code rows} of the board, for a reader who is not signed in — the top of the order
     * they asked for, not of the published one, so a column sorts the board rather than the
     * preview. {@link SharedBoardPreview} is where that selection is made.
     *
     * <p>{@code truncated} is about the gate and not about the filter: it says rows were withheld
     * because the reader is not signed in, which stays true of a board longer than the preview
     * however few rows one position happens to match.
     *
     * @param headshots as for {@link #full}
     */
    public static SharedProjectionResponse preview(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared,
            SharedBoardFilters filters, String sort, String direction, int rows,
            Set<Integer> rookieIds, boolean headshots) {
        List<com.fantasy.bff.generated.db.model.SharedPlayer> players = shared.getData().getPlayers();
        return of(shared, SharedBoardPreview.select(players, filters, sort, direction, rows),
                players.size(), players.size() > rows, rookieIds, headshots);
    }

    private static SharedProjectionResponse of(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared,
            List<com.fantasy.bff.generated.db.model.SharedPlayer> players,
            int totalPlayers, boolean truncated, Set<Integer> rookieIds, boolean headshots) {
        return new SharedProjectionResponse(
                shared.getToken(),
                shared.getName(),
                shared.getAuthorUsername(),
                shared.getSeason().getValue(),
                new SharedProjectionData(
                        ProjectionSettings.from(shared.getData().getProjectionSettings()),
                        players.stream()
                                .map(SharedPlayer::from)
                                .map(row -> headshots ? row : row.withoutHeadshot())
                                .toList()),
                totalPlayers,
                truncated,
                teamsOn(shared),
                rookiesOn(shared, rookieIds),
                shared.getCreatedAt(),
                shared.getUpdatedAt());
    }

    /**
     * The teams the filter may offer, taken from the whole board so a reader behind the gate is
     * not offered only the teams that happen to appear in the rows they were sent.
     */
    private static List<String> teamsOn(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared) {
        return shared.getData().getPlayers().stream()
                .map(com.fantasy.bff.generated.db.model.SharedPlayer::getTeamAbbrev)
                .filter(team -> team != null && !team.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /** The same for the rookie filter: who on the whole board is one, not who on this page is. */
    private static List<Integer> rookiesOn(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared,
            Set<Integer> rookieIds) {
        if (rookieIds == null || rookieIds.isEmpty()) {
            return List.of();
        }
        return shared.getData().getPlayers().stream()
                .map(com.fantasy.bff.generated.db.model.SharedPlayer::getPlayerId)
                .filter(rookieIds::contains)
                .distinct()
                .toList();
    }
}
