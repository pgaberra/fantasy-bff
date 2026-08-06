package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record PortalUrlResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Provider-hosted customer-portal URL to redirect the browser to") String portalUrl) {
}
