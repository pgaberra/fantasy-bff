package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** One team in a draft. {@code mine} marks the seat the user is drafting from. */
public record DraftTeam(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Size(max = 64) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Size(max = 64) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean mine
) {

    public static DraftTeam from(com.fantasy.bff.generated.db.model.DraftTeam team) {
        return new DraftTeam(team.getId(), team.getName(), Boolean.TRUE.equals(team.getMine()));
    }

    public com.fantasy.bff.generated.db.model.DraftTeam toDownstream() {
        return new com.fantasy.bff.generated.db.model.DraftTeam().id(id).name(name).mine(mine);
    }
}
