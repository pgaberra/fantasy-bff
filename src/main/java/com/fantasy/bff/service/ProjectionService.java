package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.CreateProjectionRequest;
import com.fantasy.bff.dto.request.ImportProjectionRequest;
import com.fantasy.bff.dto.request.ProjectionKind;
import com.fantasy.bff.dto.request.ProjectionSource;
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

@Service
public class ProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ProjectionService.class);

    /**
     * The presets a draft can be started from. The name is what the draft board shows as its
     * heading, so it is decided here rather than by the caller — otherwise a draft could claim
     * to have been drafted against something it was not.
     */
    private static final String LAST_SEASON_PRESET_NAME = "Last Season's Stats";

    private static final String MODEL_PRESET_NAME = "AI Projection";

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
     * when it was written. A reconciliation is written back rather than recomputed per read: it
     * settles what the rows are, and the next read then has nothing to do.
     */
    public ProjectionResponse get(UUID userId, UUID projectionId) {
        com.fantasy.bff.generated.db.model.ProjectionResponse stored =
                databaseServiceClient.getProjection(userId, projectionId);
        if (!keyedByThePool(stored)) {
            return ProjectionResponse.of(stored);
        }
        Optional<Reconciliation> reconciliation = reconciler.reconcile(stored.getData());
        if (reconciliation.isEmpty()) {
            return ProjectionResponse.of(stored);
        }
        keepUnacknowledged(stored.getData().getProjectionSettings(),
                reconciliation.get().addedPlayerIds());
        return ProjectionResponse.of(save(userId, projectionId, stored));
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
        if (request.kind() == ProjectionKind.PRESET_DRAFT && !isPreset(request.source())) {
            throw new IllegalArgumentException(
                    "a preset draft is defined by the server: send source=default or source=model "
                            + "and let it fill in the player rows");
        }
        if (request.source() != null) {
            if (!data.getPlayers().isEmpty()) {
                throw new IllegalArgumentException(
                        "source and data.players are mutually exclusive: omit players to have the "
                                + "server fill them in, or omit source to send your own");
            }
            data.setPlayers(playersFrom(request.source()));
            data.getProjectionSettings().setPlayerBasis(basisOf(request.source()));
        } else if (data.getPlayers().isEmpty()) {
            throw new IllegalArgumentException("data.players must not be empty unless source is set");
        }
        // Squared with the pool before it is written, not on its first read. A starting point that
        // does not cover the whole pool — the model's lines, a copied board — would otherwise have
        // that read add the players it lacked and report them to the user as having joined since
        // the projection was created, seconds earlier. Nothing is reported from here: a projection
        // that did not exist yet has no "since". A copied board's settings carry its source's
        // unacknowledged players, and those are no news to the copy either.
        data.getProjectionSettings().setUnacknowledgedNewPlayerIds(null);
        reconciler.reconcile(data);
        return ProjectionResponse.of(databaseServiceClient.createProjection(userId,
                new com.fantasy.bff.generated.db.model.CreateProjectionRequest()
                        .name(nameOf(request))
                        .kind(kindOf(request.kind()))
                        .preset(presetOf(request))
                        .data(data)
                        .playerIdSpace(idSpaceOf(playerPool.playerIdSpace()))));
    }

    /**
     * Copies a shared board into the user's own projections. The rows come across as they were
     * published, which is a snapshot of the author's player pool rather than the current one, so
     * they are squared with it and saved straight away. Left to the first read, the players the
     * snapshot lacked would be reported as having joined since the user imported it. db-service
     * writes the copy and cannot read the pool, hence the second write rather than one.
     */
    public ProjectionResponse importFromShare(UUID userId, ImportProjectionRequest request) {
        com.fantasy.bff.generated.db.model.ProjectionResponse imported =
                databaseServiceClient.importProjection(userId,
                        new com.fantasy.bff.generated.db.model.ImportProjectionRequest()
                                .token(request.token())
                                .name(request.name()));
        if (!keyedByThePool(imported) || reconciler.reconcile(imported.getData()).isEmpty()) {
            return ProjectionResponse.of(imported);
        }
        return ProjectionResponse.of(save(userId, UUID.fromString(imported.getId()), imported));
    }

    /**
     * The rows a client saves are the ones it could draw from this BFF's pool, so they are sent
     * with that pool's numbering, and db-service refuses them for a projection keyed by the other.
     */
    public ProjectionResponse update(UUID userId, UUID projectionId, UpdateProjectionRequest request) {
        request.setPlayerIdSpace(updateSpaceOf(playerPool.playerIdSpace()));
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
     * A preset draft is named here rather than by the caller. The name is what the draft board
     * shows as its heading, so letting a client choose it would let a draft claim to be drafted
     * against something it wasn't.
     */
    private static String nameOf(CreateProjectionRequest request) {
        if (request.kind() != ProjectionKind.PRESET_DRAFT) {
            return request.name();
        }
        return request.source() == ProjectionSource.MODEL
                ? MODEL_PRESET_NAME
                : LAST_SEASON_PRESET_NAME;
    }

    /**
     * Which preset a draft was started from, stored so nothing has to work it out from the name
     * later. Null unless this is a preset draft: the field says which preset, and there is none.
     */
    private static com.fantasy.bff.generated.db.model.CreateProjectionRequest.PresetEnum presetOf(
            CreateProjectionRequest request) {
        if (request.kind() != ProjectionKind.PRESET_DRAFT) {
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
        return kind == ProjectionKind.PRESET_DRAFT
                ? com.fantasy.bff.generated.db.model.CreateProjectionRequest.KindEnum.PRESET_DRAFT
                : com.fantasy.bff.generated.db.model.CreateProjectionRequest.KindEnum.PROJECTION;
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
            // No basis fits a model-seeded projection. The field answers "what should a player
            // who joins the pool later be seeded with", and the reconciler cannot answer that
            // from the model — it reads the player pool, not the projection service. Saying
            // last_season would be a lie stored in the row; the field is already documented as
            // absent on projections that predate it, and the reconciler infers when it is null.
            case MODEL -> null;
        };
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
