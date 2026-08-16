package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

/**
 * Which players are rookies for the season being projected.
 *
 * <p>{@code known} exists because "nobody is a rookie" and "we cannot say who is" are different
 * answers that would otherwise arrive as the same empty list. The historical store this is
 * derived from withholds the answer when it does not reach far enough back to tell a debut from
 * a gap in what was ingested, and it is not deployed everywhere — a client that cannot tell the
 * two apart would mark an entire league as veterans on the strength of a service being off.
 */
public record RookiesResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether rookie status could be determined at all. When false, "
                        + "playerIds is empty and means nothing — hide the marker rather than "
                        + "showing every player as a veteran.")
        boolean known,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Player ids of the rookies, in the same id space as "
                        + "/api/v1/players/skaters and /goalies.")
        List<Integer> playerIds
) {

    public static RookiesResponse unknown() {
        return new RookiesResponse(false, List.of());
    }

    public static RookiesResponse of(Set<Integer> playerIds) {
        return new RookiesResponse(true, playerIds.stream().sorted().toList());
    }
}
