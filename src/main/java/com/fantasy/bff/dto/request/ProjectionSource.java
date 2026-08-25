package com.fantasy.bff.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Where a new projection's player rows come from when the client does not send them.
 * The first two are derivable from the player read model the server already serves, so the
 * client does not have to upload ~1600 players it just downloaded; the third comes from the
 * projection model.
 */
public enum ProjectionSource {

    /** Every player starts at their current stats. */
    @JsonProperty("default")
    DEFAULT,

    /** Every player starts at zero. */
    @JsonProperty("blank")
    BLANK,

    /**
     * Every player starts at the projection model's estimate for the coming season.
     *
     * <p>Unlike the other two this is not derived from the player read model: the rows come
     * from the projection service, mapped onto the platform's player ids. Players the model
     * cannot reach are left out rather than zeroed, so a projection seeded this way covers
     * fewer players than one seeded from last season — deliberately.
     */
    @JsonProperty("model")
    MODEL
}
