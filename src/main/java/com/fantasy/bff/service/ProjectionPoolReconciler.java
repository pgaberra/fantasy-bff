package com.fantasy.bff.service;

import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.ProjectionData;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum;
import com.fantasy.bff.service.PlayerPoolRows.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps a saved projection's player rows in step with the player pool.
 *
 * <p>A projection is written once and then edited for a season, while the pool underneath it
 * keeps moving: a new season brings a new roster, and trades and call-ups add players all year.
 * A player the pool has gained is added, seeded from what the projection started as — last
 * season's stat line, zeros for one built from scratch, or the model's line for one started from
 * the AI projection. The model has no line for a player with no NHL season behind him, and that
 * is most of who arrives mid-season, so those fall back to last season's line. A goalie the model
 * reached and projects no starts for is not one of them: it has said he will not play, so he gets
 * the zero row a board built from scratch would hold, never a season the model chose not to give
 * him.
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

    private final PlayerPoolSource playerPool;
    private final PlayerPoolRows playerPoolRows;
    private final ProjectionSeedService seedService;
    private final AiProjectionAvailability aiProjection;
    private final int projectionSeason;
    private final String projectionModelVersion;

    public ProjectionPoolReconciler(PlayerPoolSource playerPool,
                                    PlayerPoolRows playerPoolRows,
                                    ProjectionSeedService seedService,
                                    AiProjectionAvailability aiProjection,
                                    @Value("${services.projection.season}") int projectionSeason,
                                    @Value("${services.projection.model-version}") String projectionModelVersion) {
        this.playerPool = playerPool;
        this.playerPoolRows = playerPoolRows;
        this.seedService = seedService;
        this.aiProjection = aiProjection;
        this.projectionSeason = projectionSeason;
        this.projectionModelVersion = projectionModelVersion;
    }

    /**
     * @param addedPlayerIds the players the pool has gained, whose rows were added — the ids
     *     rather than a count, so the client can point at the rows and not just say how many.
     *     How many rows are no longer shown is deliberately not here: the client holds the pool
     *     too, so it can see that for itself without us reporting a number that would be the same
     *     on every read until the pool moves.
     */
    public record Reconciliation(List<Integer> addedPlayerIds) {}

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
        ProjectionSettings settings = data.getProjectionSettings();
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

        Set<Integer> held = new HashSet<>();
        for (PlayerProjection player : stored) {
            held.add(player.getPlayerId());
        }
        List<Integer> added = new ArrayList<>();
        for (Integer playerId : pool.playerIds()) {
            if (!held.contains(playerId)) {
                added.add(playerId);
            }
        }
        // Asked only when someone actually arrived: the model's board is the one expensive read
        // here, and most reconciliations of a model-based projection add nobody.
        ModelBoard model = basis == PlayerBasisEnum.MODEL && !added.isEmpty()
                ? modelBoard()
                : ModelBoard.NONE;

        List<PlayerProjection> players = new ArrayList<>(stored);
        int fromModel = 0;
        int notPlaying = 0;
        for (Integer playerId : added) {
            PlayerProjection modelLine = model.lines().get(playerId);
            if (modelLine != null) {
                players.add(copyOf(modelLine));
                fromModel++;
            } else if (model.notPlaying().contains(playerId)) {
                players.add(pool.row(playerId, true));
                notPlaying++;
            } else {
                players.add(pool.row(playerId, blank));
            }
        }

        data.setPlayers(players);
        settings.setPlayerBasis(basis);
        settings.setPlayerPoolSyncedAt(syncedAt);
        if (basis == PlayerBasisEnum.MODEL && !added.isEmpty()) {
            log.info("Squared a model-based projection with the player pool: +{} added, {} from "
                    + "the model, {} zeroed as not expected to play and {} from last season",
                    added.size(), fromModel, notPlaying, added.size() - fromModel - notPlaying);
        } else if (blank && !added.isEmpty()) {
            log.info("Squared a projection with the player pool: +{} added, seeded from zeros", added.size());
        } else if (!added.isEmpty()) {
            log.info("Squared a projection with the player pool: +{} added, seeded from last season",
                    added.size());
        }
        return Optional.of(new Reconciliation(List.copyOf(added)));
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

    /**
     * What the model says about the players it reaches, by platform id: a line for each player it
     * projects, and the goalies it projects no starts for, who have no line but are not strangers
     * to it either.
     *
     * @param lines the model's current line per player
     * @param notPlaying goalies the model reached and expects not to play
     */
    private record ModelBoard(Map<Integer, PlayerProjection> lines, Set<Integer> notPlaying) {
        static final ModelBoard NONE = new ModelBoard(Map.of(), Set.of());
    }

    /**
     * The model's current board.
     *
     * <p>Premium is deliberately not asked. The projection was started from the model by someone
     * entitled to it, and a player who joins it later is part of that same projection; a lapsed
     * subscription keeps the projection it paid for, newcomers included. What is asked is whether
     * this environment serves the model at all, since with it switched off the seed endpoint
     * refuses too.
     *
     * <p>Empty when the model is off or cannot be read, which seeds every newcomer from last
     * season. That is the same fallback a player the model does not reach gets, and a failed read
     * must not cost the user the projection they were opening.
     */
    private ModelBoard modelBoard() {
        if (!aiProjection.available()) {
            return ModelBoard.NONE;
        }
        try {
            ProjectionSeedService.Seed seed = seedService.seed(projectionSeason, projectionModelVersion);
            Map<Integer, PlayerProjection> lines = new HashMap<>();
            for (PlayerProjection line : seed.players()) {
                lines.put(line.getPlayerId(), line);
            }
            return new ModelBoard(lines, seed.withoutWorkload());
        } catch (RuntimeException e) {
            log.error("Could not read the projection model; seeding a model-based projection's new "
                    + "players from last season", e);
            return ModelBoard.NONE;
        }
    }

    /** The seed's rows are shared with every reader of the cached board, so a projection gets its own. */
    private static PlayerProjection copyOf(PlayerProjection line) {
        PlayerStats stats = line.getStats();
        return new PlayerProjection()
                .playerId(line.getPlayerId())
                .type(line.getType())
                .stats(new PlayerStats()
                        .utility(new LinkedHashMap<>(stats.getUtility()))
                        .scoring(new LinkedHashMap<>(stats.getScoring())));
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

    /**
     * The watermark comes from whichever source is serving the pool: reconciling against the
     * other platform's last sync would either never fire or fire forever.
     */
    private Optional<OffsetDateTime> lastSuccessfulSync() {
        try {
            return playerPool.lastSyncedAt();
        } catch (RuntimeException e) {
            log.error("Could not read when the player pool was last synced; serving the "
                    + "projection unreconciled", e);
            return Optional.empty();
        }
    }

    private static OffsetDateTime orMin(OffsetDateTime value) {
        return value == null ? OffsetDateTime.MIN : value;
    }
}
