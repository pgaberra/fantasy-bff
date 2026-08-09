package com.fantasy.bff.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Where a new projection's player rows come from when the client does not send them.
 * Both forms are fully derivable from the player read model the server already serves,
 * so the client does not have to upload ~1600 players it just downloaded.
 */
public enum ProjectionSource {

    /** Every player starts at their current stats. */
    @JsonProperty("default")
    DEFAULT,

    /** Every player starts at zero. */
    @JsonProperty("blank")
    BLANK
}
