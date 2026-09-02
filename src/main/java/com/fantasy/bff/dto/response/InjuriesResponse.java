package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Who is hurt right now, for the players a league can roster.
 *
 * <p>{@code known} carries the same distinction as {@link RookiesResponse}: "nobody is hurt" and
 * "we could not ask" would otherwise arrive as the same empty list, and a client that could not
 * tell them apart would quietly show a full injury list as a clean bill of health for the whole
 * league. The projection service is not deployed everywhere and is switched off in production.
 *
 * <p>Unlike every other decoration on a player, this one describes <b>today</b>. It comes from a
 * source that keeps no archive, refreshed nightly, and the return dates are a club's public guess
 * restated by a broadcaster. Held for a week it is worse than nothing, because a recovered player
 * keeps reading as hurt.
 */
public record InjuriesResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the injury report could be read at all. When false, "
                        + "players is empty and means nothing — hide the marker rather than "
                        + "showing the league as fit.")
        boolean known,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "One entry per injured player, in the same id space as "
                        + "/api/v1/players/skaters and /goalies.")
        List<Injury> players
) {

    @Schema(name = "PlayerInjury")
    public record Injury(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int playerId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The report's own wording: Out, Injured Reserve, Suspension or "
                            + "Day-To-Day. Day-To-Day usually costs no games.")
            String status,

            @Schema(description = "Where he is hurt, as reported: Knee, Upper Body, Undisclosed. "
                    + "Absent when the report gives none.")
            String bodyPart,

            @Schema(description = "When he is expected back. A club's guess, so read it as the "
                    + "middle of a range rather than a date. Absent when none is given.")
            LocalDate expectedReturn
    ) {}

    public static InjuriesResponse unknown() {
        return new InjuriesResponse(false, List.of());
    }

    public static InjuriesResponse of(List<Injury> players) {
        return new InjuriesResponse(
                true, players.stream().sorted(java.util.Comparator.comparingInt(Injury::playerId)).toList());
    }
}
