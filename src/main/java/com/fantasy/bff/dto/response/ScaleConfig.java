package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Whether one stat group is scaled to the league, and which stats inside it are. */
public record ScaleConfig(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean scale,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> scalableStats
) {

    public static ScaleConfig from(com.fantasy.bff.generated.db.model.ScaleConfig config) {
        return config == null
                ? null
                : new ScaleConfig(Boolean.TRUE.equals(config.getScale()), config.getScalableStats());
    }

    public com.fantasy.bff.generated.db.model.ScaleConfig toDownstream() {
        return new com.fantasy.bff.generated.db.model.ScaleConfig()
                .scale(scale)
                .scalableStats(scalableStats);
    }
}
