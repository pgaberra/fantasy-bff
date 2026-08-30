package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.CreateProjectionRequest;
import com.fantasy.bff.dto.request.ImportProjectionRequest;
import com.fantasy.bff.dto.request.ProjectionKind;
import com.fantasy.bff.dto.request.ProjectionSource;
import com.fantasy.bff.dto.response.ProjectionResponse;
import com.fantasy.bff.dto.response.ProjectionResponse.PoolReconciliation;
import com.fantasy.bff.generated.db.model.ProjectionData;
import com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum;
import com.fantasy.bff.generated.db.model.UpdateProjectionData;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.fantasy.bff.service.ProjectionPoolReconciler.Reconciliation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
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
    private final int projectionSeason;
    private final String projectionModelVersion;

    public ProjectionService(DatabaseServiceClient databaseServiceClient,
                             PlayerPoolRows playerPoolRows,
                             PlayerPoolSource playerPool,
                             ProjectionPoolReconciler reconciler,
                             ProjectionSeedService seedService,
                             @Value("${services.projection.season}") int projectionSeason,
                             @Value("${services.projection.model-version}") String projectionModelVersion) {
        this.databaseServiceClient = databaseServiceClient;
        this.playerPoolRows = playerPoolRows;
        this.playerPool = playerPool;
        this.reconciler = reconciler;
        this.seedService = seedService;
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
        Optional<Reconciliation> reconciliation = reconciler.reconcile(stored.getData());
        if (reconciliation.isEmpty()) {
            return ProjectionResponse.of(stored);
        }
        Reconciliation change = reconciliation.get();
        return ProjectionResponse.of(
                save(userId, projectionId, stored),
                new PoolReconciliation(change.added()));
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
                            .draft(stored.getData().getDraft())));
        } catch (RuntimeException e) {
            log.error("Could not save a projection reconciled against the player pool; "
                    + "serving it unsaved", e);
            return stored;
        }
    }

    public ProjectionResponse create(UUID userId, CreateProjectionRequest request) {
        ProjectionData data = request.data();
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
     * published, which is a snapshot of the author's player pool rather than the current one —
     * squaring them with it is left to the first read, which does that for every projection
     * anyway.
     */
    public ProjectionResponse importFromShare(UUID userId, ImportProjectionRequest request) {
        return ProjectionResponse.of(databaseServiceClient.importProjection(userId,
                new com.fantasy.bff.generated.db.model.ImportProjectionRequest()
                        .token(request.token())
                        .name(request.name())));
    }

    public ProjectionResponse update(UUID userId, UUID projectionId, UpdateProjectionRequest request) {
        return ProjectionResponse.of(
                databaseServiceClient.updateProjection(userId, projectionId, request));
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
