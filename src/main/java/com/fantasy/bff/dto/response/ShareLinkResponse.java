package com.fantasy.bff.dto.response;

import com.fantasy.bff.generated.db.model.ShareResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * The owner's share, with the link already built. The web should post {@code shareUrl} as-is
 * rather than assembling it from the token, so the public path lives in one place.
 */
public record ShareLinkResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The public link to share, e.g. https://slapstat.com/s/<token>")
        String shareUrl,
        @Schema(description = "The name the public page credits, if the owner set one.") String authorAlias,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long viewCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
    public static ShareLinkResponse from(ShareResponse share, String webBaseUrl) {
        return new ShareLinkResponse(
                share.getToken(),
                webBaseUrl + "/s/" + share.getToken(),
                share.getAuthorAlias(),
                share.getViewCount(),
                toInstant(share.getCreatedAt()),
                toInstant(share.getUpdatedAt()));
    }

    private static Instant toInstant(OffsetDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
