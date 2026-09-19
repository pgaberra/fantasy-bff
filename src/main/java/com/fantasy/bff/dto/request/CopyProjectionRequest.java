package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Copies a shared board into a projection of the user's own. Unlike a follow, the copy is theirs
 * to edit and nothing the author publishes afterwards reaches it, so it carries no origin.
 */
public record CopyProjectionRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The token from the share link, i.e. the last segment of /s/{token}.")
        @NotBlank @Size(max = 64) String token,

        @Schema(description = "The shared board's `updatedAt` as the page last read it. Send it, "
                + "and a board its author has changed since is refused with 412 instead of "
                + "copied, so the reader never gets numbers they did not see: read the share "
                + "again and retry. Left out, the board is copied as it is now.")
        OffsetDateTime seenUpdatedAt
) {}
