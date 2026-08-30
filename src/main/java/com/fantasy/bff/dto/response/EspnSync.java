package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** The ESPN league a projection's scoring was last taken from, and when. */
public record EspnSync(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Size(max = 200) String leagueName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Size(max = 50) String leagueId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull OffsetDateTime syncedAt
) {

    public static EspnSync from(com.fantasy.bff.generated.db.model.EspnSync sync) {
        return sync == null
                ? null
                : new EspnSync(sync.getLeagueName(), sync.getLeagueId(), sync.getSyncedAt());
    }

    public com.fantasy.bff.generated.db.model.EspnSync toDownstream() {
        return new com.fantasy.bff.generated.db.model.EspnSync()
                .leagueName(leagueName).leagueId(leagueId).syncedAt(syncedAt);
    }
}
