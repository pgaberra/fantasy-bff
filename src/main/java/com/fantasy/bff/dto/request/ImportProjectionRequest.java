package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ImportProjectionRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The token from the share link, i.e. the last segment of /s/{token}.")
        @NotBlank @Size(max = 64) String token,

        @Schema(description = "What to call the copy. Left out, the server uses the name it was "
                + "shared under, numbering it (\"<name> (2)\") if that one is taken — so the same "
                + "board can be copied more than once. Send one to choose the name yourself; a "
                + "clash on that is refused, since it is yours to change.")
        @Size(max = 100) String name,

        @Schema(description = "The shared board's `updatedAt` as the page last read it. Send it, "
                + "and a board its author has changed since is refused with 412 instead of "
                + "copied, so the reader never gets numbers they did not see: read the share "
                + "again and retry. Left out, the board is copied as it is now.")
        OffsetDateTime seenUpdatedAt
) {}
