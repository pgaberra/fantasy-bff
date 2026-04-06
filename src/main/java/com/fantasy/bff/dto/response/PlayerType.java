package com.fantasy.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PlayerType {
    @JsonProperty("skater") SKATER,
    @JsonProperty("goalie") GOALIE
}
