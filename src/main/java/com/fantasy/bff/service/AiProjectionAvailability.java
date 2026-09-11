package com.fantasy.bff.service;

import com.fantasy.bff.config.AiProjectionProperties;
import com.fantasy.bff.config.SecurityProperties;
import org.springframework.stereotype.Component;

/**
 * Whether this environment serves the AI projection at all: the one answer every door to the
 * model's lines asks, and the one the web is told through {@code GET /api/v1/features}.
 *
 * <p>Two settings feed it. {@code ai-projection.enabled} switches the model off while leaving
 * Who's hot up, and {@code security.projection-model-enabled} closes the whole
 * {@code /api/v1/projection-model} prefix. The seed endpoint sits behind both, so a model-seeded
 * projection has to as well: with only the first checked, an environment that closed the prefix
 * still handed the model's lines out through {@code source=model}.
 *
 * <p>It says nothing about premium. A locked AI projection is still available; only one this
 * environment does not serve is not.
 */
@Component
public class AiProjectionAvailability {

    private final boolean available;

    public AiProjectionAvailability(AiProjectionProperties aiProjection, SecurityProperties security) {
        this.available = aiProjection.enabled() && security.projectionModelEnabled();
    }

    public boolean available() {
        return available;
    }
}
