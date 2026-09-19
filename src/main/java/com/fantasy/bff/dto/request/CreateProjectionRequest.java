package com.fantasy.bff.dto.request;

import com.fantasy.bff.generated.db.model.ProjectionData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creating a projection. The BFF owns this shape rather than reusing db-service's generated
 * model, because {@code source} is a concern of this API only: db-service always stores a
 * complete projection.
 *
 * <p>{@code kind} is likewise a concern of this API: db-service stores whichever kind it is
 * told, while the app decides that a draft started from a preset gets a {@code DRAFT} so it
 * never shows up among the projections the user made. A preset is defined by the server, so
 * such a draft takes its name and its player rows from here and not from the caller — see
 * {@code ProjectionService}. A draft against a board of the user's own is not created here at
 * all: {@code POST /api/v1/projections/{id}/drafts} copies that board server-side.
 *
 * <p>A projection covers every player in the league, which is ~0.5 MB of JSON the client had
 * just downloaded from {@code /api/v1/players/*}. Uploading it back was failing in production
 * for at least one user (JAVA-SPRING-BOOT-J), so a client that wants the standard starting
 * point sends {@code source} and leaves {@code data.players} empty; the server fills it in
 * from the same read model. Clients with genuinely user-specific rows — copying an existing
 * projection, or redeeming one edited in the landing-page demo — still send them.
 */
public record CreateProjectionRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Ignored for a preset draft, which the server names itself.")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "What the projection is for. Defaults to the user's own. A preset "
                + "draft requires source=default and is named by the server. An imported board "
                + "may be created with the caller's own rows (a spreadsheet import): it takes "
                + "data.players, refuses source, and carries no share origin.")
        ProjectionKind kind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid ProjectionData data,

        @Schema(description = "Fill data.players from the server's player read model instead of "
                + "sending them. Requires data.players to be empty; omit it to send your own rows.")
        ProjectionSource source
) {}
