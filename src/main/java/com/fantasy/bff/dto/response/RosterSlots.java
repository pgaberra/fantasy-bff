package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * How many of each position a team starts, as the league sets it. The caps are a sanity bound,
 * not a rule any league is near: no format starts fifty centres.
 */
public record RosterSlots(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int c,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int lw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int rw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int d,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int util,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int bn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int g
) {

    public static RosterSlots from(com.fantasy.bff.generated.db.model.RosterSlots slots) {
        return slots == null
                ? null
                : new RosterSlots(slots.getC(), slots.getLw(), slots.getRw(), slots.getD(),
                        slots.getUtil(), slots.getBn(), slots.getG());
    }

    public com.fantasy.bff.generated.db.model.RosterSlots toDownstream() {
        return new com.fantasy.bff.generated.db.model.RosterSlots()
                .c(c).lw(lw).rw(rw).d(d).util(util).bn(bn).g(g);
    }
}
