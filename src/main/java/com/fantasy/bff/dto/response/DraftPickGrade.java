package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** How a pick compares with where the model ranked the player. */
@Schema(description = "How a pick compares with where the model ranked the player: STEAL and GOOD took him "
        + "later than his rank, FAIR about on it, REACH and BIG_REACH earlier. UNRANKED is a player the "
        + "model has no line for.")
public enum DraftPickGrade {
    STEAL,
    GOOD,
    FAIR,
    REACH,
    BIG_REACH,
    UNRANKED
}
