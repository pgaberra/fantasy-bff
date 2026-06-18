package com.fantasy.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ScoringBasis {
    POINTS("points"),
    CATEGORY("category");

    private final String value;

    ScoringBasis(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
