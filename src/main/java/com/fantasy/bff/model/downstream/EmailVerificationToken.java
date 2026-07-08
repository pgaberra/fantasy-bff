package com.fantasy.bff.model.downstream;

import java.time.Instant;

public record EmailVerificationToken(
        String token,
        Instant expiresAt
) {}
