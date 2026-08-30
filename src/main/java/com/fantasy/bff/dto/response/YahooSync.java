package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** The Yahoo league a projection's scoring was last taken from, and when. */
public record YahooSync(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Size(max = 200) String leagueName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Size(max = 100) String leagueKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull OffsetDateTime syncedAt
) {

    public static YahooSync from(com.fantasy.bff.generated.db.model.YahooSync sync) {
        return sync == null
                ? null
                : new YahooSync(sync.getLeagueName(), sync.getLeagueKey(), sync.getSyncedAt());
    }

    public com.fantasy.bff.generated.db.model.YahooSync toDownstream() {
        return new com.fantasy.bff.generated.db.model.YahooSync()
                .leagueName(leagueName).leagueKey(leagueKey).syncedAt(syncedAt);
    }
}
