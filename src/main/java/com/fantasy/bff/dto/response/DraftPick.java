package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** One pick: which player went to which team. */
public record DraftPick(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Size(max = 64) String teamId
) {

    public static DraftPick from(com.fantasy.bff.generated.db.model.DraftPick pick) {
        return new DraftPick(pick.getPlayerId(), pick.getTeamId());
    }

    public com.fantasy.bff.generated.db.model.DraftPick toDownstream() {
        return new com.fantasy.bff.generated.db.model.DraftPick().playerId(playerId).teamId(teamId);
    }
}
