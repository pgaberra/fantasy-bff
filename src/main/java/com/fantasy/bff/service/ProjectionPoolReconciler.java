package com.fantasy.bff.service;

import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.ProjectionData;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum;
import com.fantasy.bff.generated.yahoo.model.SyncRunResponse;
import com.fantasy.bff.service.PlayerPoolRows.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps a saved projection's player rows in step with the player pool.
 *
 * <p>A projection is written once and then edited for a season, while the pool underneath it
 * keeps moving: a new season brings a new roster, and trades and call-ups add players all year.
 * A player the pool has gained is added, seeded from what the projection started as — last
 * season's stat line, or zeros for one built from scratch.
 *
 * <p><b>Nothing is ever removed.</b> A row whose player has left the pool is left exactly where
 * it is and simply not shown, which the client already does for any row it cannot draw. Deleting
 * it would be irreversible and the pool is not reliable enough to bet a user's work on: a
 * truncated fetch, or a player Yahoo momentarily stops listing, would cost them numbers they
 * cannot get back. Kept, those rows come back into view by themselves when the pool recovers.
 * The cost is a few dozen stale rows a season against some sixteen hundred live ones.
 *
 * <p>The work is guarded by the sync run the rows were last squared with, so reading a projection
 * costs a full pool read at most once per sync rather than on every open.
 */
@Service
public class ProjectionPoolReconciler {

    private static final Logger log = LoggerFactory.getLogger(ProjectionPoolReconciler.class);

    /**
     * How much of a projection has to be untouched zeros before we read it as one started from
     * scratch. Only projections saved before the basis was recorded need guessing at, and the two
     * starting points are far apart: a projection seeded from last season has a stat line for
     * every player who played, a blank one has none at all.
     */
    private static final double BLANK_SHARE = 0.9;

    private static final int SYNC_RUNS_TO_SCAN = 10;

    private final PlayerServiceClient playerServiceClient;
    private final PlayerPoolRows playerPoolRows;

    public ProjectionPoolReconciler(PlayerServiceClient playerServiceClient, PlayerPoolRows playerPoolRows) {
        this.playerServiceClient = playerServiceClient;
        this.playerPoolRows = playerPoolRows;
    }

    /**
     * @param added rows added for players the pool has gained. How many rows are no longer shown
     *     is deliberately not here: the client holds the pool too, so it can see that for itself
     *     without us reporting a number that would be the same on every read until the pool moves.
     */
    public record Reconciliation(int added) {}

    /**
     * Squares {@code data}'s rows with the pool, in place. Empty when there was nothing to do —
     * the rows are already squared with the latest sync, nothing has ever been synced, or the
     * pool could not be read.
     */
    public Optional<Reconciliation> reconcile(ProjectionData data) {
        OffsetDateTime syncedAt = lastSuccessfulSync().orElse(null);
        if (syncedAt == null) {
            return Optional.empty();
        }
        ProjectionSettings settings = data.getSettings();
        if (settings.getPlayerBasis() != null && syncedAt.isEqual(orMin(settings.getPlayerPoolSyncedAt()))) {
            return Optional.empty();
        }

        Pool pool = readPool().orElse(null);
        if (pool == null) {
            return Optional.empty();
        }

        List<PlayerProjection> stored = data.getPlayers();
        PlayerBasisEnum basis = settings.getPlayerBasis() == null
                ? inferBasis(stored)
                : settings.getPlayerBasis();
        boolean blank = basis == PlayerBasisEnum.BLANK;

        List<PlayerProjection> players = new ArrayList<>(stored);
        Set<Integer> held = new HashSet<>();
        for (PlayerProjection player : stored) {
            held.add(player.getPlayerId());
        }
        int added = 0;
        for (Integer playerId : pool.playerIds()) {
            if (!held.contains(playerId)) {
                players.add(pool.row(playerId, blank));
                added++;
            }
        }

        data.setPlayers(players);
        settings.setPlayerBasis(basis);
        settings.setPlayerPoolSyncedAt(syncedAt);
        if (added > 0) {
            log.info("Squared a projection with the player pool: +{} added, seeded from {}",
                    added, blank ? "zeros" : "last season");
        }
        return Optional.of(new Reconciliation(added));
    }

    /**
     * A projection saved before the basis was recorded is read off its own rows: one started from
     * scratch is almost entirely zeros, one started from last season's stats is not.
     */
    private static PlayerBasisEnum inferBasis(List<PlayerProjection> players) {
        if (players.isEmpty()) {
            return PlayerBasisEnum.LAST_SEASON;
        }
        long blank = players.stream().filter(ProjectionPoolReconciler::isBlank).count();
        return blank >= players.size() * BLANK_SHARE ? PlayerBasisEnum.BLANK : PlayerBasisEnum.LAST_SEASON;
    }

    private static boolean isBlank(PlayerProjection player) {
        PlayerStats stats = player.getStats();
        return allZero(stats.getUtility()) && allZero(stats.getScoring());
    }

    private static boolean allZero(Map<String, Double> stats) {
        return stats == null || stats.values().stream().allMatch(value -> value == null || value == 0.0);
    }

    /**
     * The pool is the only thing that says which players exist, so a read that fails must leave
     * the projection alone rather than drop every row it cannot account for. Same for an empty
     * pool: the read model is never legitimately empty, so that is a fault, not a roster of none.
     */
    private Optional<Pool> readPool() {
        Pool pool;
        try {
            pool = playerPoolRows.read();
        } catch (RuntimeException e) {
            log.error("Could not read the player pool; serving the projection unreconciled", e);
            return Optional.empty();
        }
        if (pool.isEmpty()) {
            log.error("The player pool is empty; serving the projection unreconciled");
            return Optional.empty();
        }
        return Optional.of(pool);
    }

    private Optional<OffsetDateTime> lastSuccessfulSync() {
        try {
            return playerServiceClient.getSyncRuns(SYNC_RUNS_TO_SCAN).stream()
                    .filter(run -> "success".equals(run.getStatus()))
                    .map(SyncRunResponse::getFinishedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());
        } catch (RuntimeException e) {
            log.error("Could not read the player sync runs; serving the projection unreconciled", e);
            return Optional.empty();
        }
    }

    private static OffsetDateTime orMin(OffsetDateTime value) {
        return value == null ? OffsetDateTime.MIN : value;
    }
}
