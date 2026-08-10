package com.fantasy.bff.dto.request;

import com.fantasy.bff.generated.db.model.SharedPlayer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Publishing a projection. The rows come from the client because the ranking is computed there —
 * fantasy value depends on the league's scoring settings and the whole player pool, neither of
 * which the server ranks today. Everything else on the public page (name, season, settings) is
 * copied from the stored projection downstream, so a client cannot publish a page that claims
 * to be something the projection is not.
 */
public record ShareProjectionRequest(

        @Schema(description = "Name to be credited as on the public page. Omit to be credited as "
                + "nobody — the account's email is never shown.")
        @Size(max = 40) String authorAlias,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The ranked rows to publish, in display order.")
        @NotNull @Valid @Size(max = 200) List<SharedPlayer> players
) {}
