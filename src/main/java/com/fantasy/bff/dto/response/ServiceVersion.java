package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ServiceVersion(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean up,
        @Schema(description = "Deployed version; absent when the service is unreachable") String version
) {}
