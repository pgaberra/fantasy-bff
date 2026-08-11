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
 * copied from the stored projection downstream, and the page is credited to the owner's account
 * username, so a client cannot publish a page that claims to be something — or someone — it is not.
 */
public record ShareProjectionRequest(


        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The ranked rows to publish, in display order.")
        @NotNull @Valid @Size(max = 200) List<SharedPlayer> players
) {}
