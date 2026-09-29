package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/**
 * How many of each position a team starts, as the league sets it. The caps are a sanity bound,
 * not a rule any league is near: no format starts fifty centres.
 *
 * <p>{@code w} (wing: LW or RW) and {@code f} (forward: C, LW or RW) are the leagues' forward
 * flex slots. A client or a stored board that predates them sends none, which reads as 0.
 */
public record RosterSlots(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int c,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int lw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int rw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Wing flex slots, filled by a LW or a RW. Read as 0 when absent.")
        @Min(0) @Max(50) Integer w,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Forward flex slots, filled by a C, a LW or a RW. Read as 0 when absent.")
        @Min(0) @Max(50) Integer f,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int d,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int util,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int bn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(50) int g
) {

    public RosterSlots {
        w = Objects.requireNonNullElse(w, 0);
        f = Objects.requireNonNullElse(f, 0);
    }

    /** Every skater slot a team fills, flex and bench included. */
    public int skaterSlots() {
        return c + lw + rw + w + f + d + util + bn;
    }

    public static RosterSlots from(com.fantasy.bff.generated.db.model.RosterSlots slots) {
        return slots == null
                ? null
                : new RosterSlots(slots.getC(), slots.getLw(), slots.getRw(),
                        Objects.requireNonNullElse(slots.getW(), 0),
                        Objects.requireNonNullElse(slots.getF(), 0),
                        slots.getD(), slots.getUtil(), slots.getBn(), slots.getG());
    }

    public com.fantasy.bff.generated.db.model.RosterSlots toDownstream() {
        return new com.fantasy.bff.generated.db.model.RosterSlots()
                .c(c).lw(lw).rw(rw).w(w).f(f).d(d).util(util).bn(bn).g(g);
    }
}
