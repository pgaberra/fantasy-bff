package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Whether each half of the pool is ranked by its projected stats or by hand, and the hand-placed
 * order.
 *
 * <p>Split by player type because that is how the choice is made: an owner who projects every
 * skater stat by stat may still want to say nothing more about goalies than which one he would
 * rather have.
 */
public record ManualRanking(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid PlayerTypeRanking skater,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid PlayerTypeRanking goalie
) {

    /** Both halves are required downstream, so a ranking either arrives whole or not at all. */
    public static ManualRanking from(com.fantasy.bff.generated.db.model.ManualRanking ranking) {
        if (ranking == null) {
            return null;
        }
        return new ManualRanking(
                PlayerTypeRanking.from(ranking.getSkater()),
                PlayerTypeRanking.from(ranking.getGoalie()));
    }

    public com.fantasy.bff.generated.db.model.ManualRanking toDownstream() {
        return new com.fantasy.bff.generated.db.model.ManualRanking()
                .skater(skater.toDownstream())
                .goalie(goalie.toDownstream());
    }
}
