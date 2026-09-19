package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Renaming a board or a draft, without sending the board with it. */
public record RenameProjectionRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "True when the app derived the name rather than the user typing it "
                + "— a draft taking the name of the league it was just synced with. Such a "
                + "rename is skipped where the user has named the row themselves, and a name "
                + "another row holds is numbered rather than refused.")
        Boolean derived
) {}
