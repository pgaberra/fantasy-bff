package com.fantasy.bff.service;

import com.fantasy.bff.config.AiProjectionProperties;
import org.springframework.stereotype.Component;

/**
 * Whether this environment serves the AI projection at all: the one answer every door to the
 * model's lines asks, and the one the web is told through {@code GET /api/v1/features}.
 *
 * <p>{@code ai-projection.enabled} switches the model off while leaving Who's hot (the game-range
 * splits under the same {@code /api/v1/projection-model} prefix) up.
 *
 * <p>It says nothing about premium. A locked AI projection is still available; only one this
 * environment does not serve is not.
 */
@Component
public class AiProjectionAvailability {

    private final boolean available;

    public AiProjectionAvailability(AiProjectionProperties aiProjection) {
        this.available = aiProjection.enabled();
    }

    public boolean available() {
        return available;
    }
}
