package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.generated.espn.model.PlayerStatLine;
import com.fantasy.bff.generated.espn.model.PlayerStatsResponse;
import com.fantasy.bff.service.mapping.EspnStatLineIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serves the ESPN stat lines the player responses are enriched with, held in memory between
 * refreshes.
 *
 * <p>espn-service refreshes them once a night, so re-fetching them on every player request
 * would be pure overhead on the hottest endpoint in the app. A failure is never fatal: the
 * player list is Yahoo's and must keep loading, so an unreachable espn-service costs the four
 * ESPN-only stats and nothing else.
 */
@Component
public class EspnPlayerStatsProvider {

    private static final Logger log = LoggerFactory.getLogger(EspnPlayerStatsProvider.class);

    /**
     * A failed load is retried on this cadence rather than on the next request: without it, an
     * espn-service outage would put a downstream timeout in front of every single player load.
     */
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);

    private record Cached(EspnStatLineIndex index, Instant expiresAt) {}

    private final EspnServiceClient espnServiceClient;
    private final Duration ttl;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public EspnPlayerStatsProvider(EspnServiceClient espnServiceClient,
                                   @Value("${services.espn-fantasy.player-stats-ttl-ms:1800000}") long ttlMs) {
        this.espnServiceClient = espnServiceClient;
        this.ttl = Duration.ofMillis(ttlMs);
    }

    public EspnStatLineIndex index() {
        Cached cached = cache.get();
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return cached.index();
        }
        try {
            PlayerStatsResponse response = espnServiceClient.playerStats();
            List<PlayerStatLine> statLines =
                    response == null || response.getPlayers() == null ? List.of() : response.getPlayers();
            EspnStatLineIndex index = new EspnStatLineIndex(statLines);
            cache.set(new Cached(index, Instant.now().plus(ttl)));
            log.info("Loaded {} ESPN stat lines for player enrichment", statLines.size());
            return index;
        } catch (Exception e) {
            log.error("Failed to load ESPN stat lines; serving players without the ESPN-only stats", e);
            // Keep serving a stale index rather than dropping the stats over a blip.
            EspnStatLineIndex fallback = cached != null ? cached.index() : EspnStatLineIndex.empty();
            cache.set(new Cached(fallback, Instant.now().plus(RETRY_AFTER_FAILURE)));
            return fallback;
        }
    }
}
