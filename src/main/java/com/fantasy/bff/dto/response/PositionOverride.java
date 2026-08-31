package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The positions an owner set by hand for one skater, replacing the ones the player read model
 * reports. The platforms do not agree on eligibility — a skater Yahoo lists at LW and RW may be
 * LW only in ESPN — and only the owner knows which league they are drafting for.
 */
public record PositionOverride(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int playerId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @NotEmpty @Size(max = 4) List<SkaterPosition> positions
) {

    public static PositionOverride from(com.fantasy.bff.generated.db.model.PositionOverride override) {
        return new PositionOverride(
                override.getPlayerId(),
                override.getPositions().stream()
                        .map(position -> SkaterPosition.valueOf(position.getValue()))
                        .toList());
    }

    public com.fantasy.bff.generated.db.model.PositionOverride toDownstream() {
        return new com.fantasy.bff.generated.db.model.PositionOverride()
                .playerId(playerId)
                .positions(positions.stream()
                        .map(position -> com.fantasy.bff.generated.db.model.PositionOverride
                                .PositionsEnum.fromValue(position.name()))
                        .toList());
    }
}
