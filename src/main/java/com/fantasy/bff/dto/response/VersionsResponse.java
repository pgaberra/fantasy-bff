package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record VersionsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ServiceVersion> services,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Which platform the player pool is being served from — the live "
                        + "wiring, not the configured value, so a setting that did not take is "
                        + "visible rather than assumed.",
                allowableValues = {"yahoo", "espn"})
        String playerSource
) {}
