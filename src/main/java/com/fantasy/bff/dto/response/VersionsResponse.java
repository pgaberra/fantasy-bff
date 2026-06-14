package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record VersionsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ServiceVersion> services
) {}
