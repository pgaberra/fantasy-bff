package com.fantasy.bff.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What a projection is for. A preset draft is not a projection the user made — it only exists
 * to hold the picks of a draft started from a preset (currently last season's stats), so it is
 * kept out of the projections the app lists as their own. Neither is an imported board: it is a
 * copy of someone else's, taken from their share link.
 */
public enum ProjectionKind {

    /** A projection the user created and edits. */
    @JsonProperty("projection")
    PROJECTION,

    /** Storage for a draft started from a preset rather than from a projection. */
    @JsonProperty("preset_draft")
    PRESET_DRAFT,

    /**
     * A board the user brought in rather than built: a copy taken from a share link, or rows the
     * client read out of a spreadsheet. Only the first carries an origin.
     */
    @JsonProperty("imported")
    IMPORTED
}
