package com.fantasy.bff.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Where a new projection's player rows come from when the client does not send them.
 * The first two are derivable from the player read model the server already serves, so the
 * client does not have to upload ~1600 players it just downloaded; the other two come from the
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
    MODEL,

    /**
     * Every player starts at the projection model's line for what is left of a season under way:
     * the line Team Power Rankings ranks on, the rest of the season lifted toward the table it
     * will finish on, with the games already played left out. Only while a season is under way;
     * before its first game and after its last the model has no such line, and this is refused.
     */
    @JsonProperty("rest_of_season")
    REST_OF_SEASON
}
