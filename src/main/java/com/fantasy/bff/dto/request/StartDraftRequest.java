package com.fantasy.bff.dto.request;

import com.fantasy.bff.generated.db.model.ProjectionData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Starting a draft against one of the user's own boards. The player rows are not sent: the
 * server copies them from the board being drafted against, which is ~0.5 MB of JSON the client
 * already has and would otherwise upload back.
 */
public record StartDraftRequest(

        @Schema(description = "What to call the draft. Defaults to the board's name. A name "
                + "another draft holds is numbered (\"Board (2)\") rather than refused, so the "
                + "saved name is the one in the response.")
        @Size(max = 100) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The draft's own league and its setup. `players` is ignored: the "
                        + "rows are copied from the board.")
        @NotNull @Valid ProjectionData data
) {}
