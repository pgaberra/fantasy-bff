package com.fantasy.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** One player's row in a saved projection: who, which stat line, and the numbers. */
public record PlayerProjection(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int playerId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Type type,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid PlayerStats stats
) {

    /** Which stat line the row carries. */
    public enum Type {
        @JsonProperty("skater")
        SKATER,
        @JsonProperty("goalie")
        GOALIE
    }

    public static PlayerProjection from(com.fantasy.bff.generated.db.model.PlayerProjection row) {
        return new PlayerProjection(
                row.getPlayerId(),
                row.getType() == com.fantasy.bff.generated.db.model.PlayerProjection.TypeEnum.GOALIE
                        ? Type.GOALIE
                        : Type.SKATER,
                PlayerStats.from(row.getStats()));
    }

    public com.fantasy.bff.generated.db.model.PlayerProjection toDownstream() {
        return new com.fantasy.bff.generated.db.model.PlayerProjection()
                .playerId(playerId)
                .type(type == Type.GOALIE
                        ? com.fantasy.bff.generated.db.model.PlayerProjection.TypeEnum.GOALIE
                        : com.fantasy.bff.generated.db.model.PlayerProjection.TypeEnum.SKATER)
                .stats(stats.toDownstream());
    }
}
