package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether the app shows players' pictures.
 *
 * <p>Off unless the configuration says otherwise, which is the opposite of
 * {@code ai-projection.enabled} and for a different reason than {@code payments.enabled}: the
 * pictures are the platform's photographs, and we hold no licence to show them in a product with
 * a paid tier. A deployment that forgets the variable must not quietly bring them back.
 *
 * <p>Off takes them away where they leave this service, not in the browser: {@code PlayerService}
 * sends every pool player without a headshot address and answers the headshot endpoint with
 * nothing, without asking the platform, and a shared board's rows go out without the address its
 * snapshot stored. The web already draws initials for a player with no picture, so it has no
 * switch of its own.
 */
@ConfigurationProperties(prefix = "players.avatars")
public record PlayerAvatarsProperties(Boolean enabled) {

    public PlayerAvatarsProperties {
        enabled = Boolean.TRUE.equals(enabled);
    }
}
