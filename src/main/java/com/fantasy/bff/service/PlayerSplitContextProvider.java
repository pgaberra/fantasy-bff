package com.fantasy.bff.service;

import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import com.fantasy.bff.service.mapping.PlayerIdOverrides;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import com.fantasy.bff.service.mapping.PlayerIdResolver.Candidate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Serves the NHL-id → platform-id mapping the splits are joined on, held in memory between
 * refreshes.
 *
 * <p>Building it means reading the whole active player list from the projection service and the
 * whole player read model from yahoo-service, then matching the two by identity. That is the
 * same answer every time until a roster moves, but it was being rebuilt per request — and Who's
 * hot asks twice on every page load, once for skaters and once for goalies. Four large reads and
 * two full matching passes to serve one screen, all racing each other, is how a page that works
 * fine on its own starts timing out against its own load.
 *
 * <p>A failure falls back to the last mapping rather than to nothing: the ids do not go stale in
 * minutes, and a blip in either upstream should cost freshness, not the whole leaderboard.
 */
@Component
public class PlayerSplitContextProvider {

    private static final Logger log = LoggerFactory.getLogger(PlayerSplitContextProvider.class);

    /**
     * A failed refresh is retried on this cadence rather than on the next request: without it, an
     * upstream outage would put two downstream timeouts in front of every single page load.
     */
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);

    /**
     * The mapping plus the NHL-side identities, which is everything a split row needs.
     *
     * @param defenceEligible platform ids of players a league would slot at defence. Defencemen
     *     points are scored as their own category, and eligibility is the platform's answer —
     *     the NHL's listed position doesn't decide what a fantasy league lets you start.
     */
    public record Context(
            PlayerIdMapping mapping,
            Map<Long, PlayerResponse> identities,
            Set<Integer> defenceEligible) {

        public Integer platformId(Integer nhlId) {
            return nhlId == null ? null : mapping.nhlIdToPlatformId().get(nhlId.longValue());
        }

        public boolean playsDefence(int platformId) {
            return defenceEligible.contains(platformId);
        }
    }

    private record Cached(Context context, Instant expiresAt) {}

    private final ProjectionServiceClient projectionServiceClient;
    private final PlayerServiceClient playerServiceClient;
    private final PlayerIdResolver resolver;
    private final PlayerIdOverrides overrides;
    private final Duration ttl;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public PlayerSplitContextProvider(
            ProjectionServiceClient projectionServiceClient,
            PlayerServiceClient playerServiceClient,
            PlayerIdResolver resolver,
            PlayerIdOverrides overrides,
            @Value("${services.projection.player-mapping-ttl-ms:1800000}") long ttlMs) {
        this.projectionServiceClient = projectionServiceClient;
        this.playerServiceClient = playerServiceClient;
        this.resolver = resolver;
        this.overrides = overrides;
        this.ttl = Duration.ofMillis(ttlMs);
    }

    public Context context() {
        Cached cached = cache.get();
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return cached.context();
        }
        try {
            Context refreshed = load();
            cache.set(new Cached(refreshed, Instant.now().plus(ttl)));
            return refreshed;
        } catch (Exception e) {
            if (cached == null) {
                // Nothing to fall back to, so the caller has to hear about it — an empty mapping
                // would render as "no players were hot", which is a lie about the data.
                throw new IllegalStateException("Failed to build the player id mapping", e);
            }
            log.error("Failed to refresh the player id mapping; serving the previous one", e);
            cache.set(new Cached(cached.context(), Instant.now().plus(RETRY_AFTER_FAILURE)));
            return cached.context();
        }
    }

    private Context load() {
        List<PlayerResponse> nhlPlayers = projectionServiceClient.activePlayers();
        Map<Long, PlayerResponse> identities = new HashMap<>();
        List<Candidate> nhlCandidates = new ArrayList<>();
        for (PlayerResponse player : nhlPlayers) {
            identities.put(player.getNhlId().longValue(), player);
            nhlCandidates.add(new Candidate(
                    player.getNhlId().longValue(),
                    player.getFullName(),
                    player.getCurrentTeam(),
                    player.getSweaterNumber()));
        }

        List<Candidate> platform = new ArrayList<>();
        Set<Integer> defenceEligible = new HashSet<>();
        for (SkaterResponse skater : playerServiceClient.getSkaters()) {
            platform.add(new Candidate(
                    skater.id(), skater.name(), skater.teamAbbrev(), skater.sweaterNumber()));
            if (skater.positions() != null && skater.positions().contains(SkaterPosition.D)) {
                defenceEligible.add(skater.id());
            }
        }
        for (GoalieResponse goalie : playerServiceClient.getGoalies()) {
            platform.add(new Candidate(
                    goalie.id(), goalie.name(), goalie.teamAbbrev(), goalie.sweaterNumber()));
        }

        return new Context(
                resolver.resolve(nhlCandidates, platform, overrides.asMap()),
                identities,
                defenceEligible);
    }
}
