package com.fantasy.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/** Which projection a league's picks are totalled against. */
@Schema(description = "The projection a league summary scores its picks against")
public enum SummarySource {

    /** The model's own lines for the coming season. */
    MODEL("model"),

    /** What the players actually did last season. */
    LAST_SEASON("last_season");

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
}
