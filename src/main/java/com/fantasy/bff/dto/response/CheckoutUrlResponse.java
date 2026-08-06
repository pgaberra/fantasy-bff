package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record CheckoutUrlResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Provider-hosted checkout URL to redirect the browser to") String checkoutUrl) {
}
