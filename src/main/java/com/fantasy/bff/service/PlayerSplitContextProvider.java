package com.fantasy.bff.service;

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
import java.util.Optional;
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
     * @param rookies platform ids of players the projection service calls rookies for the season
     *     being projected. Empty when it declined to say — which is not the same as nobody being
     *     one, so callers must be able to tell the two apart.
     */
    public record Context(
            PlayerIdMapping mapping,
            Map<Long, PlayerResponse> identities,
            Set<Integer> defenceEligible,
            Optional<Set<Integer>> rookies) {

        public Integer platformId(Integer nhlId) {
            return nhlId == null ? null : mapping.nhlIdToPlatformId().get(nhlId.longValue());
        }

        public boolean playsDefence(int platformId) {
            return defenceEligible.contains(platformId);
        }
    }

    private record Cached(Context context, Instant expiresAt) {}

    private final ProjectionServiceClient projectionServiceClient;
    private final PlayerPoolSource playerPool;
    private final PlayerIdResolver resolver;
    private final PlayerIdOverrides overrides;
    private final Duration ttl;
    private final int season;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public PlayerSplitContextProvider(
            ProjectionServiceClient projectionServiceClient,
            PlayerPoolSource playerPool,
            PlayerIdResolver resolver,
            PlayerIdOverrides overrides,
            @Value("${services.projection.player-mapping-ttl-ms:1800000}") long ttlMs,
            @Value("${services.projection.season}") int season) {
        this.projectionServiceClient = projectionServiceClient;
        this.playerPool = playerPool;
        this.resolver = resolver;
        this.overrides = overrides;
        this.ttl = Duration.ofMillis(ttlMs);
        this.season = season;
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
        List<PlayerResponse> nhlPlayers = projectionServiceClient.activePlayers(season);
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
        for (SkaterResponse skater : playerPool.getSkaters()) {
            platform.add(new Candidate(
                    skater.id(), skater.name(), skater.teamAbbrev(), skater.sweaterNumber()));
            if (skater.positions() != null && skater.positions().contains(SkaterPosition.D)) {
                defenceEligible.add(skater.id());
            }
        }
        for (GoalieResponse goalie : playerPool.getGoalies()) {
            platform.add(new Candidate(
                    goalie.id(), goalie.name(), goalie.teamAbbrev(), goalie.sweaterNumber()));
        }

        PlayerIdMapping mapping = resolver.resolve(nhlCandidates, platform, overrides.asMap());
        return new Context(mapping, identities, defenceEligible, rookies(nhlPlayers, mapping));
    }

    /**
     * A player the projection service leaves undecided leaves the whole answer undecided: it
     * withholds rookie status wholesale when its history is too shallow to tell, and a partial
     * "these are the rookies" would read as a complete one.
     */
    private static Optional<Set<Integer>> rookies(
            List<PlayerResponse> nhlPlayers, PlayerIdMapping mapping) {
        Set<Integer> rookies = new HashSet<>();
        for (PlayerResponse player : nhlPlayers) {
            Boolean rookie = player.getRookie();
            if (rookie == null) {
                return Optional.empty();
            }
            Integer platformId = mapping.nhlIdToPlatformId().get(player.getNhlId().longValue());
            if (Boolean.TRUE.equals(rookie) && platformId != null) {
                rookies.add(platformId);
            }
        }
        return Optional.of(rookies);
    }
}
