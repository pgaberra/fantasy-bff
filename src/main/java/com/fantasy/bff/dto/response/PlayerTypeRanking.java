package com.fantasy.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** How one half of the pool is ordered, and the order if the owner gave one. */
public record PlayerTypeRanking(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether this player type is ordered by its projected stats or by "
                        + "the order the owner put it in.")
        @NotNull RankingMode mode,

        @Schema(description = "The player ids the owner placed by hand, best first. Players of "
                + "this type that are absent keep their projected order below the placed ones, so "
                + "an owner who ranked only a top twenty does not have to place the rest. Ignored "
                + "while mode is `projected`. The cap matches the player list.")
        @Size(max = 2000) List<@NotNull Integer> order
) {

    /** What a player type's places come from. */
    public enum RankingMode {
        @JsonProperty("projected")
        PROJECTED,
        @JsonProperty("manual")
        MANUAL
    }

    public static PlayerTypeRanking from(
            com.fantasy.bff.generated.db.model.PlayerTypeRanking ranking) {
        if (ranking == null) {
            return null;
        }
        return new PlayerTypeRanking(
                ranking.getMode() == com.fantasy.bff.generated.db.model.PlayerTypeRanking.ModeEnum.MANUAL
                        ? RankingMode.MANUAL
                        : RankingMode.PROJECTED,
                ranking.getOrder());
    }

    public com.fantasy.bff.generated.db.model.PlayerTypeRanking toDownstream() {
        return new com.fantasy.bff.generated.db.model.PlayerTypeRanking()
                .mode(mode == RankingMode.MANUAL
                        ? com.fantasy.bff.generated.db.model.PlayerTypeRanking.ModeEnum.MANUAL
                        : com.fantasy.bff.generated.db.model.PlayerTypeRanking.ModeEnum.PROJECTED)
                .order(order);
    }
}
