package com.fantasy.bff.service.mapping;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Manually pinned NHL id → platform id pairs, for the players name matching cannot reach.
 *
 * <p>{@link PlayerIdResolver} deliberately refuses to guess in three cases: two players sharing a
 * name, a team and a jersey number; a name spelled too differently to match on either form
 * (transliterations — Semyon/Semen, Aleksei/Alexei); and a last-name-and-initial that is itself
 * ambiguous on the platform's side. A wrong match is worse than none, since it hangs one player's
 * projection on another player.
 *
 * <p>Holding these here rather than in code means correcting one is an environment variable and a
 * restart, not a release. The moment anyone notices a missing player is mid-draft, which is the
 * worst possible time to need a deployment.
 *
 * <p>Format is {@code nhlId:platformId} pairs, comma separated:
 * {@code PROJECTION_PLAYER_ID_OVERRIDES="8480012:5432,8471234:9876"}.
 */
// final: the constructor validates and throws, and a non-final class doing so is open to a
// finalizer attack (SpotBugs CT_CONSTRUCTOR_THROW).
@Component
public final class PlayerIdOverrides {

    private static final Logger log = LoggerFactory.getLogger(PlayerIdOverrides.class);

    private final Map<Long, Integer> pairs;

    public PlayerIdOverrides(@Value("${services.projection.player-id-overrides:}") String raw) {
        this.pairs = parse(raw);
    }

    /** NHL id → platform id. Empty when nothing is configured, which is the normal case. */
    public Map<Long, Integer> asMap() {
        return pairs;
    }

    @PostConstruct
    void logConfigured() {
        // Silence would leave you unable to tell a typo'd variable from one that never loaded.
        if (!pairs.isEmpty()) {
            log.info("Loaded {} manual player id overrides", pairs.size());
        }
    }

    /**
     * Rejects anything malformed rather than skipping it. An override that is silently dropped
     * looks identical to one that was never needed — and it would only be discovered by the
     * player going missing again, long after the config was written.
     */
    private static Map<Long, Integer> parse(String raw) {
        Map<Long, Integer> parsed = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        for (String entry : raw.split(",")) {
            String pair = entry.trim();
            if (pair.isEmpty()) {
                continue;
            }
            String[] sides = pair.split(":");
            if (sides.length != 2) {
                throw new IllegalArgumentException(
                        "Malformed player id override '" + pair + "'; expected nhlId:platformId");
            }
            long nhlId;
            int platformId;
            try {
                nhlId = Long.parseLong(sides[0].trim());
                platformId = Integer.parseInt(sides[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Non-numeric player id override '" + pair + "'", e);
            }
            Integer previous = parsed.put(nhlId, platformId);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate player id override for NHL id " + nhlId);
            }
        }
        // Insertion order is kept so the parse is deterministic; Map.copyOf would not.
        return Collections.unmodifiableMap(parsed);
    }
}
