package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.CopyProjectionRequest;
import com.fantasy.bff.dto.request.CreateProjectionRequest;
import com.fantasy.bff.dto.request.ImportProjectionRequest;
import com.fantasy.bff.dto.request.ProjectionKind;
import com.fantasy.bff.dto.request.ProjectionSource;
import com.fantasy.bff.dto.request.RenameProjectionRequest;
import com.fantasy.bff.dto.request.StartDraftRequest;
import com.fantasy.bff.dto.response.ProjectionSummaryResponse;
import com.fantasy.bff.dto.response.ProjectionResponse;
import com.fantasy.bff.exception.PremiumRequiredException;
import com.fantasy.bff.generated.db.model.ProjectionData;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum;
import com.fantasy.bff.generated.db.model.UpdateProjectionData;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.fantasy.bff.service.ProjectionPoolReconciler.Reconciliation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

@Service
public class ProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ProjectionService.class);

    private final DatabaseServiceClient databaseServiceClient;
    private final PlayerPoolRows playerPoolRows;
    private final PlayerPoolSource playerPool;
    private final ProjectionPoolReconciler reconciler;
    private final ProjectionSeedService seedService;
    private final AiProjectionAvailability aiProjection;
    private final EntitlementService entitlementService;
    private final int projectionSeason;
    private final String projectionModelVersion;

    public ProjectionService(DatabaseServiceClient databaseServiceClient,
                             PlayerPoolRows playerPoolRows,
                             PlayerPoolSource playerPool,
                             ProjectionPoolReconciler reconciler,
                             ProjectionSeedService seedService,
                             AiProjectionAvailability aiProjection,
                             EntitlementService entitlementService,
                             @Value("${services.projection.season}") int projectionSeason,
                             @Value("${services.projection.model-version}") String projectionModelVersion) {
        this.databaseServiceClient = databaseServiceClient;
        this.playerPoolRows = playerPoolRows;
        this.playerPool = playerPool;
        this.reconciler = reconciler;
        this.seedService = seedService;
        this.aiProjection = aiProjection;
        this.entitlementService = entitlementService;
        this.projectionSeason = projectionSeason;
        this.projectionModelVersion = projectionModelVersion;
    }

    /**
     * Reads a projection and squares its player rows with the current pool first, so a projection
     * opened after a player sync covers the players that exist now rather than the ones that did
     * when it was written.
     */
    public ProjectionResponse get(UUID userId, UUID projectionId) {
        com.fantasy.bff.generated.db.model.ProjectionResponse stored =
                databaseServiceClient.getProjection(userId, projectionId);
        return ProjectionResponse.of(reconciled(userId, projectionId, stored));
    }

    /**
     * Squares a stored projection with the pool and decides whether the result is worth keeping.
     *
     * <p>On a board the user owns it is: the reconciliation settles what the rows are, the write
     * back leaves the next read with nothing to do, and the players it added are kept on the
     * projection until the owner acknowledges them.
     *
     * <p>On a <em>follow</em> it is not. A follow is db-service's mirror of someone else's board
     * and holds nothing of the follower's but their draft, so a write back stores none of the
     * reconciled rows — it would cost a round trip to save nothing, and a read that reported
     * rows the next write could not keep would be inconsistent with what is stored. So a follow
     * is reconciled in memory on every read and never written. Its new players are not reported
     * either: the follower did not build this board and cannot edit it, so there is nothing for
     * them to do about a player who joined, and the list the author had not acknowledged on his
     * own board travelled across with the publish and is none of their business.
     */
    private com.fantasy.bff.generated.db.model.ProjectionResponse reconciled(
            UUID userId, UUID projectionId,
            com.fantasy.bff.generated.db.model.ProjectionResponse stored) {
        if (!keyedByThePool(stored)) {
            return stored;
        }
        Optional<Reconciliation> reconciliation = reconciler.reconcile(stored.getData());
        if (isFollow(stored)) {
            clearUnacknowledged(stored);
            return stored;
        }
        if (reconciliation.isEmpty()) {
            return stored;
        }
        keepUnacknowledged(stored.getData().getProjectionSettings(),
                reconciliation.get().addedPlayerIds());
        return save(userId, projectionId, stored);
    }

    /**
     * Drops the list of players waiting to be acknowledged. On a follow it is the author's own
     * unread notice, travelled across with the publish; on a fresh copy it is the same list,
     * about a board the user took seconds ago. Neither is theirs to be nagged about.
     */
    private static void clearUnacknowledged(
            com.fantasy.bff.generated.db.model.ProjectionResponse stored) {
        Optional.ofNullable(stored.getData())
                .map(ProjectionData::getProjectionSettings)
                .ifPresent(settings -> settings.setUnacknowledgedNewPlayerIds(null));
    }

    /**
     * A followed board, as opposed to one of the user's own. The origin is what says so: it is
     * the link the row mirrors, and db-service sets it on a follow and on nothing else — a copy
     * taken from a link is the user's own board and carries none.
     */
    private static boolean isFollow(com.fantasy.bff.generated.db.model.ProjectionResponse stored) {
        return stored.getOrigin() != null;
    }

    /**
     * Whether the stored rows are numbered the way the pool this BFF serves is. When they are
     * not, squaring them with the pool would add every player under the other platform's ids
     * beside the stored ones, and the saved result could not be taken apart again. That happens
     * only while the pool is being switched: between the remap and the BFF serving the new pool.
     */
    private boolean keyedByThePool(com.fantasy.bff.generated.db.model.ProjectionResponse stored) {
        String poolSpace = playerPool.playerIdSpace().name();
        boolean keyedByThePool = Optional.ofNullable(stored.getPlayerIdSpace())
                .map(space -> space.name().equals(poolSpace))
                .orElse(true);
        if (!keyedByThePool) {
            log.warn("A projection is keyed by the other platform's player ids than the pool this "
                    + "BFF serves; serving it unreconciled");
        }
        return keyedByThePool;
    }

    /**
     * The players a read adds are kept on the projection until its owner acknowledges them, so
     * the notice about them survives a reload and follows them to another device. A second pool
     * change before then adds to the list rather than replacing it.
     */
    private static void keepUnacknowledged(ProjectionSettings settings, List<Integer> added) {
        if (added.isEmpty()) {
            return;
        }
        List<Integer> earlier = settings.getUnacknowledgedNewPlayerIds();
        Set<Integer> unacknowledged = earlier == null ? new LinkedHashSet<>() : new LinkedHashSet<>(earlier);
        unacknowledged.addAll(added);
        settings.setUnacknowledgedNewPlayerIds(new ArrayList<>(unacknowledged));
    }

    /**
     * Saving the reconciliation is what stops the next read redoing it — but it is not what the
     * caller asked for. A write that fails must not cost them the projection they were opening,
     * so the reconciled rows are served anyway and the next read tries again.
     */
    private com.fantasy.bff.generated.db.model.ProjectionResponse save(
            UUID userId, UUID projectionId, com.fantasy.bff.generated.db.model.ProjectionResponse stored) {
        try {
            return databaseServiceClient.updateProjection(userId, projectionId, new UpdateProjectionRequest()
                    .name(stored.getName())
                    .data(new UpdateProjectionData()
                            .projectionSettings(stored.getData().getProjectionSettings())
                            .players(stored.getData().getPlayers())
                            .draft(stored.getData().getDraft())
                            .positionOverrides(stored.getData().getPositionOverrides()))
                    .playerIdSpace(updateSpaceOf(playerPool.playerIdSpace())));
        } catch (RuntimeException e) {
            log.error("Could not save a projection reconciled against the player pool; "
                    + "serving it unsaved", e);
            return stored;
        }
    }

    public ProjectionResponse create(UUID userId, CreateProjectionRequest request) {
        ProjectionData data = request.data();
        if (request.kind() == ProjectionKind.IMPORTED && request.source() != null) {
            throw new IllegalArgumentException(
                    "an imported board brings its own rows: send data.players and omit source");
        }
        // Checked before anything else a model-seeded request would go on to do, so an
        // environment with the AI projection switched off never reaches the projection service.
        // The web drops the preset on the same answer, read from /api/v1/features; this is what
        // makes it a refusal rather than a hidden button.
        if (request.source() == ProjectionSource.MODEL) {
            if (!aiProjection.available()) {
                throw new IllegalArgumentException(
                        "source=model is unavailable: the AI projection is switched off in this "
                                + "environment");
            }
            // The model's lines are what premium pays for, so a projection seeded from them is
            // refused here as well as at /projection-model/seed. Both matter: the seed endpoint
            // is how the new-projection page fills a board, and this is how a preset draft does
            // — a client that skipped the first would otherwise still get the model through the
            // second. Checked after the switch above, since an environment without the feature
            // has nothing to sell.
            requirePremiumFor(userId);
        }
        if (request.kind() == ProjectionKind.DRAFT && !isPreset(request.source())) {
            throw new IllegalArgumentException(
                    "a draft created here is a draft against a preset, and the preset is defined "
                            + "by the server: send source=default or source=model and let it fill "
                            + "in the player rows. To draft against a board of your own, POST "
                            + "/api/v1/projections/{id}/drafts instead");
        }
        if (request.source() != null) {
            if (!data.getPlayers().isEmpty()) {
                throw new IllegalArgumentException(
                        "source and data.players are mutually exclusive: omit players to have the "
                                + "server fill them in, or omit source to send your own");
            }
            fillFrom(request.source(), data);
        } else if (data.getPlayers().isEmpty()) {
            throw new IllegalArgumentException("data.players must not be empty unless source is set");
        } else {
            // A new projection has nothing stored to have held the basis already.
            settleClaimedBasis(userId, data.getProjectionSettings(), () -> false);
        }
        squareWithPool(data);
        return ProjectionResponse.of(databaseServiceClient.createProjection(userId,
                new com.fantasy.bff.generated.db.model.CreateProjectionRequest()
                        .name(request.name())
                        .kind(kindOf(request.kind()))
                        .preset(presetOf(request))
                        .data(data)
                        .playerIdSpace(idSpaceOf(playerPool.playerIdSpace()))));
    }

    /**
     * The rows a projection created with {@code source=model} is written with, without writing
     * one: the model's lines, squared with the pool exactly as {@link #create} squares them. What
     * the new-projection page ranks to preview the AI starting point, so its top rows are the top
     * rows of the board it creates; the two share {@link #fillFrom} and {@link #squareWithPool}
     * so they cannot come apart. Availability and premium are the caller's to check.
     */
    public List<com.fantasy.bff.dto.response.PlayerProjection> modelBoard() {
        ProjectionData data = new ProjectionData()
                .projectionSettings(new ProjectionSettings())
                .players(new ArrayList<>());
        fillFrom(ProjectionSource.MODEL, data);
        squareWithPool(data);
        return data.getPlayers().stream()
                .map(com.fantasy.bff.dto.response.PlayerProjection::from)
                .toList();
    }

    /**
     * Fills a new projection's rows from a starting point the server owns, and records that
     * starting point as the basis. The rows have never been squared with any sync, so a stamp
     * the client sent with its settings is dropped: left standing, it would have the
     * reconciliation skip the players the starting point does not cover.
     */
    private void fillFrom(ProjectionSource source, ProjectionData data) {
        data.setPlayers(playersFrom(source));
        data.getProjectionSettings().setPlayerBasis(basisOf(source));
        data.getProjectionSettings().setPlayerPoolSyncedAt(null);
    }

    /**
     * Squares a projection that does not exist yet with the pool. A starting point that does not
     * cover the whole pool — the model's lines, a copied board — would otherwise have its first
     * read add the players it lacked and report them to the user as having joined since the
     * projection was created, seconds earlier. Nothing is reported from here: a projection that
     * did not exist yet has no "since". A copied board's settings carry its source's
     * unacknowledged players, and those are no news to the copy either.
     */
    private void squareWithPool(ProjectionData data) {
        data.getProjectionSettings().setUnacknowledgedNewPlayerIds(null);
        reconciler.reconcile(data);
    }

    /**
     * Starts a draft against one of the user's own boards. db-service copies the board's rows
     * into a draft of its own, so the ~0.5 MB the caller would otherwise download and upload
     * again never leaves the servers — and so the board stays editable, and deletable, while the
     * draft is under way.
     *
     * <p>The rows are the board's, which this BFF already squared with the pool when the board
     * was last read, so nothing is reconciled here: a draft is a snapshot by design, and adding
     * players to it after it started would move the numbers under somebody mid-draft.
     */
    public ProjectionResponse startDraft(UUID userId, UUID boardId, StartDraftRequest request) {
        return ProjectionResponse.of(databaseServiceClient.startDraft(userId, boardId,
                new com.fantasy.bff.generated.db.model.StartDraftRequest()
                        .name(request.name())
                        .data(request.data())));
    }

    /**
     * Renames a board or a draft. A name the user typed is refused where it is taken; one the app
     * derived — a draft taking the name of the league it was just synced with — is numbered
     * instead, and is skipped where the user has named the row themselves.
     */
    public ProjectionSummaryResponse rename(UUID userId, UUID id, RenameProjectionRequest request) {
        return ProjectionSummaryResponse.from(databaseServiceClient.renameProjection(userId, id,
                new com.fantasy.bff.generated.db.model.RenameProjectionRequest()
                        .name(request.name())
                        .derived(request.derived())));
    }

    /**
     * Follows a shared board: db-service records the link and mirrors the board behind it, and
     * rewrites that mirror every time the author publishes again. Nothing is written back from
     * here — a follow holds nothing of the follower's but their draft — so the rows are squared
     * with the pool in memory, exactly as they are on every later read.
     *
     * @return the follow, and whether it was created now rather than already held
     */
    public Followed follow(UUID userId, ImportProjectionRequest request) {
        DatabaseServiceClient.FollowedProjection followed =
                databaseServiceClient.followShare(userId,
                        new com.fantasy.bff.generated.db.model.ImportProjectionRequest()
                                .token(request.token())
                                .seenUpdatedAt(request.seenUpdatedAt()));
        com.fantasy.bff.generated.db.model.ProjectionResponse stored = followed.projection();
        if (keyedByThePool(stored)) {
            reconciler.reconcile(stored.getData());
        }
        clearUnacknowledged(stored);
        return new Followed(ProjectionResponse.of(stored), followed.created());
    }

    /** @param created false when the user already followed this link and it was handed back. */
    public record Followed(ProjectionResponse projection, boolean created) {}

    /**
     * Copies a shared board into a projection of the user's own. The rows come across as they
     * were published, which is a snapshot of the author's player pool rather than the current
     * one, so they are squared with it and saved straight away — this board is the user's, so
     * the reconciliation is theirs to keep. Left to the first read, the players the snapshot
     * lacked would be reported as having joined since they took the copy. db-service writes the
     * copy and cannot read the pool, hence the second write rather than one.
     */
    public ProjectionResponse copyFromShare(UUID userId, CopyProjectionRequest request) {
        com.fantasy.bff.generated.db.model.ProjectionResponse copied =
                databaseServiceClient.copyShare(userId,
                        new com.fantasy.bff.generated.db.model.CopyProjectionRequest()
                                .token(request.token())
                                .seenUpdatedAt(request.seenUpdatedAt()));
        if (!keyedByThePool(copied) || reconciler.reconcile(copied.getData()).isEmpty()) {
            return ProjectionResponse.of(copied);
        }
        // Nothing is reported: a board copied seconds ago has no "since", and the players the
        // author had not acknowledged on his own board are no news to the copy either.
        clearUnacknowledged(copied);
        return ProjectionResponse.of(save(userId, UUID.fromString(copied.getId()), copied));
    }

    /**
     * The rows a client saves are the ones it could draw from this BFF's pool, so they are sent
     * with that pool's numbering, and db-service refuses them for a projection keyed by the other.
     */
    public ProjectionResponse update(UUID userId, UUID projectionId, UpdateProjectionRequest request) {
        request.setPlayerIdSpace(updateSpaceOf(playerPool.playerIdSpace()));
        Optional.ofNullable(request.getData())
                .map(UpdateProjectionData::getProjectionSettings)
                .ifPresent(settings -> settleClaimedBasis(userId, settings, () ->
                        databaseServiceClient.getProjection(userId, projectionId)
                                .getData().getProjectionSettings().getPlayerBasis() == PlayerBasisEnum.MODEL));
        return ProjectionResponse.of(
                databaseServiceClient.updateProjection(userId, projectionId, request));
    }

    private static UpdateProjectionRequest.PlayerIdSpaceEnum updateSpaceOf(PlayerIdSpace space) {
        return UpdateProjectionRequest.PlayerIdSpaceEnum.valueOf(space.name());
    }

    /**
     * The rows are keyed by whichever pool is wired in, so that is what the stored projection is
     * stamped with. Rows the client sent rather than the server filling them in are keyed the
     * same way: they came from this BFF's player endpoints in the first place.
     */
    private static com.fantasy.bff.generated.db.model.CreateProjectionRequest.PlayerIdSpaceEnum
            idSpaceOf(PlayerIdSpace space) {
        return space == PlayerIdSpace.ESPN
                ? com.fantasy.bff.generated.db.model.CreateProjectionRequest.PlayerIdSpaceEnum.ESPN
                : com.fantasy.bff.generated.db.model.CreateProjectionRequest.PlayerIdSpaceEnum.YAHOO;
    }

    /**
     * Which preset a draft was started from, stored so nothing has to work it out from the name
     * later. Null unless this is a preset draft: the field says which preset, and there is none.
     */
    private static com.fantasy.bff.generated.db.model.CreateProjectionRequest.PresetEnum presetOf(
            CreateProjectionRequest request) {
        if (request.kind() != ProjectionKind.DRAFT) {
            return null;
        }
        return request.source() == ProjectionSource.MODEL
                ? com.fantasy.bff.generated.db.model.CreateProjectionRequest.PresetEnum.MODEL
                : com.fantasy.bff.generated.db.model.CreateProjectionRequest.PresetEnum.LAST_SEASON;
    }

    /**
     * Refuses a model-seeded projection to an account without premium. The web keeps the AI
     * projection visible and marked rather than hiding it, so this is the refusal behind a
     * button a free account can see and is meant to see.
     */
    private void requirePremiumFor(UUID userId) {
        if (!entitlementService.hasPremiumAccess(userId.toString())) {
            throw new PremiumRequiredException(
                    "The AI projection is part of premium. Subscribe to start a projection from "
                            + "the model's own lines.");
        }
    }

    /** Which sources define a preset: one that fills every row from something the server owns. */
    private static boolean isPreset(ProjectionSource source) {
        return source == ProjectionSource.DEFAULT || source == ProjectionSource.MODEL;
    }

    private static com.fantasy.bff.generated.db.model.CreateProjectionRequest.KindEnum kindOf(
            ProjectionKind kind) {
        if (kind == null) {
            return com.fantasy.bff.generated.db.model.CreateProjectionRequest.KindEnum.PROJECTION;
        }
        return switch (kind) {
            case DRAFT -> com.fantasy.bff.generated.db.model.CreateProjectionRequest.KindEnum.DRAFT;
            case IMPORTED -> com.fantasy.bff.generated.db.model.CreateProjectionRequest.KindEnum.IMPORTED;
            case PROJECTION -> com.fantasy.bff.generated.db.model.CreateProjectionRequest.KindEnum.PROJECTION;
        };
    }

    /**
     * What the rows started as is stored with them, because it is also the answer to what a
     * player who joins the pool later should be seeded with. A caller that sends its own rows —
     * a copy, or a projection carried over from the demo — brings the basis with them.
     */
    private static PlayerBasisEnum basisOf(ProjectionSource source) {
        return switch (source) {
            case BLANK -> PlayerBasisEnum.BLANK;
            case DEFAULT -> PlayerBasisEnum.LAST_SEASON;
            // Recorded outright. Left null, the reconciliation that runs before the write read
            // the model's rows as last season's and stored that, so newcomers never saw the model.
            case MODEL -> PlayerBasisEnum.MODEL;
        };
    }

    /**
     * A model basis is what makes the reconciler hand out the model's lines, and the model is what
     * premium pays for, so the basis is not the client's to claim. A client sending it with its
     * own rows would otherwise have the next reconciliation fill every row it left out from the
     * model. It stands when the account has premium, or when the projection already held it —
     * a lapsed subscription keeps the projection it started, and its app sends the basis back on
     * every save. Anything else is read as last season, the fallback the model basis has anyway.
     *
     * @param storedIsModel whether the projection already holds a model basis; asked only when it
     *     could decide the answer, since it costs a read
     */
    private void settleClaimedBasis(UUID userId, ProjectionSettings settings, BooleanSupplier storedIsModel) {
        if (settings == null || settings.getPlayerBasis() != PlayerBasisEnum.MODEL) {
            return;
        }
        if (entitlementService.hasPremiumAccess(userId.toString()) || storedIsModel.getAsBoolean()) {
            return;
        }
        log.warn("A client claimed a model basis for a projection without premium or a stored model "
                + "basis; recording last season instead");
        settings.setPlayerBasis(PlayerBasisEnum.LAST_SEASON);
    }

    /**
     * The player rows a new projection starts from. {@code DEFAULT} keeps each player's current
     * stats and {@code BLANK} zeroes them — the two starting points the client used to build
     * locally and upload. {@code MODEL} instead takes the projection model's lines.
     */
    private List<com.fantasy.bff.generated.db.model.PlayerProjection> playersFrom(ProjectionSource source) {
        if (source == ProjectionSource.MODEL) {
            return seedService.seed(projectionSeason, projectionModelVersion).players();
        }
        return playerPoolRows.read().all(source == ProjectionSource.BLANK);
    }
}
