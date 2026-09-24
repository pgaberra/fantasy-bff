package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * A shared projection as a visitor reads it: the snapshot db-service stores, plus what it cannot
 * know — who on the board is a rookie, and whether this environment shows players' pictures.
 * Every reader gets the whole board, signed in or not.
 */
public record SharedProjectionResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The owner's public name. Read live rather than snapshotted: a "
                        + "rename should follow onto links already shared.")
        String authorUsername,

        @Schema(description = "The author's profile picture, as a path relative to this API, or "
                        + "absent where they have none. The address carries the stamp on the "
                        + "picture, so replacing it is a new address rather than a cached old one.")
        String authorAvatar,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The season this projection is for, as its 8-digit code.")
        String season,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The snapshot: every row the owner published.")
        SharedProjectionData data,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every team with a player on the published board, alphabetically.")
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
     * @param rookieIds who is a rookie, or null where nobody can say
     * @param headshots whether this environment shows players' pictures; without them each row
     *     goes out with no headshot, whatever address the snapshot stored
     */
    public static SharedProjectionResponse of(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared,
            Set<Integer> rookieIds, boolean headshots) {
        return new SharedProjectionResponse(
                shared.getToken(),
                shared.getName(),
                shared.getAuthorUsername(),
                authorAvatarPath(shared),
                shared.getSeason().getValue(),
                new SharedProjectionData(
                        ProjectionSettings.from(shared.getData().getProjectionSettings()),
                        shared.getData().getPlayers().stream()
                                .map(SharedPlayer::from)
                                .map(row -> headshots ? row : row.withoutHeadshot())
                                .toList()),
                teamsOn(shared),
                rookiesOn(shared, rookieIds),
                shared.getCreatedAt(),
                shared.getUpdatedAt());
    }

    /**
     * Where the page fetches the author's picture, or null where they have none. A path rather
     * than the bytes: the picture is the same for every reader of a link and is worth a cache
     * entry of its own, which a field on this response could never be. The stamp rides along as
     * {@code v} so a replaced picture is fetched rather than read out of that cache.
     */
    private static String authorAvatarPath(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared) {
        OffsetDateTime updatedAt = shared.getAuthorAvatarUpdatedAt();
        if (updatedAt == null) {
            return null;
        }
        return "/shared/" + shared.getToken() + "/avatar?v=" + updatedAt.toEpochSecond();
    }

    /** The teams the filter may offer. */
    private static List<String> teamsOn(
            com.fantasy.bff.generated.db.model.SharedProjectionResponse shared) {
        return shared.getData().getPlayers().stream()
                .map(com.fantasy.bff.generated.db.model.SharedPlayer::getTeamAbbrev)
                .filter(team -> team != null && !team.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /** Who on the board is a rookie, for the rookie filter and the mark beside a name. */
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
