package com.fantasy.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Which projection a league's picks are totalled against. */
@Schema(description = "The projection a league summary scores its picks against")
public enum SummarySource {

    /** The model's own lines for the coming season. */
    MODEL("model"),

    /** What the players actually did last season. */
    LAST_SEASON("last_season"),

    /** One of the user's own boards, or one they follow, named by its id. */
    PROJECTION("projection");

    private final String value;

    SummarySource(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** The source a request names, defaulting to the model where it names none. */
    public static SummarySource from(String value) {
        if (value == null || value.isBlank()) {
            return MODEL;
        }
        for (SummarySource source : values()) {
            if (source.value.equalsIgnoreCase(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown summary source: " + value);
    }

    /**
     * The source a request names, given the board it may also name. A board implies
     * {@link #PROJECTION}; naming either one without the other, or a board beside another source,
     * is a request that cannot be answered.
     */
    public static SummarySource from(String value, UUID projectionId) {
        boolean named = value != null && !value.isBlank();
        if (projectionId == null) {
            SummarySource source = from(value);
            if (source == PROJECTION) {
                throw new IllegalArgumentException("source=projection needs a projectionId");
            }
            return source;
        }
        if (named && from(value) != PROJECTION) {
            throw new IllegalArgumentException("A projectionId goes only with source=projection");
        }
        return PROJECTION;
    }
}
