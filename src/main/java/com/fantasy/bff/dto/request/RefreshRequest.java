package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @Schema(description = "Legacy: the refresh token, for a client that does not send the "
                + "slapstat_refresh cookie yet. The cookie wins when both arrive.")
        @Size(max = 4096) String refreshToken
) {}
