package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Where the player pool comes from. There are two: Yahoo, which the app was built on, and
 * ESPN, which took over when Yahoo stopped serving its game-wide player collection.
 *
 * <p>Exactly one is wired in, chosen by {@code players.source}. Both hand back the same shape,
 * so nothing downstream — and nothing in the browser — can tell which one answered. What they
 * cannot share is the player id: Yahoo's ids and ESPN's ids are different numbers for the same
 * person, and a stored projection is keyed by whichever was in use when it was saved. Switching
 * the source is therefore not reversible on its own; it goes with migrating the stored ids.
 */
public interface PlayerPoolSource {

    /**
     * Which platform this pool comes from. Reported on {@code /api/v1/versions} so an operator
     * can read which source is live rather than infer it from the players coming back — the
     * switch is an environment variable, and an environment variable that did not take looks
     * exactly like one that was never set.
     */
    String platform();

    /**
     * Which platform's numbering this pool's ids are. Asked of the source rather than read off
     * configuration, because the source is the thing that produced the numbers — a projection
     * filled from here is stamped with the answer, and a remap later trusts that stamp.
     */
    PlayerIdSpace playerIdSpace();

    List<SkaterResponse> getSkaters();

    List<GoalieResponse> getGoalies();

    /** The player's headshot as PNG bytes, empty when the source has no picture for them. */
    Optional<byte[]> getHeadshot(int playerId);

    /**
     * When the source last refreshed its pool, empty when it has never managed to or could not
     * be asked. It is the watermark a saved projection is squared against, so it has to come
     * from whichever source is actually serving the pool — the other one's last sync says
     * nothing about the players being shown.
     */
    Optional<OffsetDateTime> lastSyncedAt();
}
