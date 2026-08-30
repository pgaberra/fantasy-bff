package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Saving a projection: its name, and everything in it. */
public record UpdateProjectionRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(max = 100) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid UpdateProjectionData data
) {

    public com.fantasy.bff.generated.db.model.UpdateProjectionRequest toDownstream() {
        return new com.fantasy.bff.generated.db.model.UpdateProjectionRequest()
                .name(name)
                .data(data.toDownstream());
    }
}
