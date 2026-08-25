package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImportProjectionRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The token from the share link, i.e. the last segment of /s/{token}.")
        @NotBlank @Size(max = 64) String token,

        @Schema(description = "What to call the copy. Defaults to the name it was shared under; "
                + "send one to resolve a clash with a projection already imported.")
        @Size(max = 100) String name
) {}
