package com.fantasy.bff.model.downstream;

import java.time.Instant;

public record PasswordResetToken(
        String token,
        Instant expiresAt
) {}
