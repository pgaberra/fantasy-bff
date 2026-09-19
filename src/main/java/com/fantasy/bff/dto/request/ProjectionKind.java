package com.fantasy.bff.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What a saved board is. A draft is not a projection the user made — it is a copy of the board
 * it was drafted against, kept apart so the picks live somewhere of their own and so one board
 * can be drafted against as many times as its owner likes. Neither is an imported board: it is a
 * copy of someone else's, taken from their share link.
 */
public enum ProjectionKind {

    /** A projection the user created and edits. */
    @JsonProperty("projection")
    PROJECTION,

    /**
     * A draft: the picks, the league it is played in, and a copy of the numbers it is drafted
     * against. Started from one of the user's boards (POST /projections/{id}/drafts) or from a
     * preset, which is the one case the rows are seeded here.
     */
    @JsonProperty("draft")
    DRAFT,

    /**
     * A board the user brought in rather than built: a copy taken from a share link, or rows the
     * client read out of a spreadsheet. Only the first carries an origin.
     */
    @JsonProperty("imported")
    IMPORTED
}
