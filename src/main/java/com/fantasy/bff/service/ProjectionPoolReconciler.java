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
 * Rows for players the pool no longer carries are dropped, and a player it has gained is added —
 * seeded from what the projection started as, so a projection built on last season's stats gains
 * that player's stat line and one built from scratch gains zeros.
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
     * @param added rows added for players the pool has gained
     * @param removed rows dropped for players it no longer carries
     */
    public record Reconciliation(int added, int removed) {}

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

        List<PlayerProjection> players = new ArrayList<>(pool.size());
        Set<Integer> kept = new HashSet<>();
        for (PlayerProjection player : stored) {
            if (pool.contains(player.getPlayerId())) {
                players.add(player);
                kept.add(player.getPlayerId());
            }
        }
        int removed = stored.size() - players.size();
        int added = 0;
        for (Integer playerId : pool.playerIds()) {
            if (!kept.contains(playerId)) {
                players.add(pool.row(playerId, blank));
                added++;
            }
        }

        data.setPlayers(players);
        settings.setPlayerBasis(basis);
        settings.setPlayerPoolSyncedAt(syncedAt);
        if (added > 0 || removed > 0) {
            log.info("Squared a projection with the player pool: +{} added, -{} dropped, seeded from {}",
                    added, removed, blank ? "zeros" : "last season");
        }
        return Optional.of(new Reconciliation(added, removed));
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
