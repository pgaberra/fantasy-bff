package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether this environment offers the AI projection: the model-seeded starting point
 * ({@code source=model}, as a projection or as a preset draft) and the model lines the
 * new-projection page previews it with.
 *
 * <p>On unless the configuration says otherwise, which is the opposite of {@code payments.enabled}
 * and for the opposite reason: payments have never shipped, the AI projection has. A deployment
 * that forgets the variable must keep offering a live feature rather than silently drop it.
 *
 * <p>It says nothing about the game-range splits served under the same path prefix. Those are
 * measured numbers behind Who's hot, not the model's estimates, and they stay up either way.
 */
@ConfigurationProperties(prefix = "ai-projection")
public record AiProjectionProperties(Boolean enabled) {

    public AiProjectionProperties {
        enabled = enabled == null || enabled;
    }
}
