package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import com.fantasy.bff.service.mapping.PlayerIdResolver.Candidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ESPN player ids in the numbering the pool is served in, for picks read from an ESPN league.
 *
 * <p>Over an ESPN pool that is the id itself. Over a Yahoo pool nobody publishes a crosswalk, so
 * it is built the way {@link PlayerIdRemapService} builds one: the whole ESPN pool matched against
 * the whole served pool on identity. Whole pools rather than just the players drafted, because the
 * resolver's guards against a near-miss name look for a namesake on the side being matched, and a
 * draft's handful of players would hide the namesake that should have stopped it.
 *
 * <p>That is two full pools, so the result is held for a while: a draft room polls every few
 * seconds, and neither platform renumbers a player between syncs.
 */
@Component
public class EspnPoolIdCrosswalk {

    private static final Logger log = LoggerFactory.getLogger(EspnPoolIdCrosswalk.class);

    private record Cached(Map<Long, Integer> poolIdByEspnId, Instant expiresAt) {}

    private final PlayerPoolSource pool;
    private final EspnServiceClient espnServiceClient;
    private final PlayerIdResolver resolver;
    private final int statsSeason;
    private final Duration ttl;
    private Cached cached;

    public EspnPoolIdCrosswalk(PlayerPoolSource pool,
                               EspnServiceClient espnServiceClient,
                               PlayerIdResolver resolver,
                               @Value("${services.espn-fantasy.stats-season}") int statsSeason,
                               @Value("${league-draft-sync.espn-crosswalk-ttl-ms:1800000}") long ttlMs) {
        this.pool = pool;
        this.espnServiceClient = espnServiceClient;
        this.resolver = resolver;
        this.statsSeason = statsSeason;
        this.ttl = Duration.ofMillis(ttlMs);
    }

    /** The pool's id for each ESPN id that has one; an ESPN player with no counterpart is left out. */
    public Map<Long, Integer> poolIds(Collection<Long> espnIds) {
        Map<Long, Integer> poolIds = new HashMap<>();
        if (espnIds.isEmpty()) {
            return poolIds;
        }
        if (pool.playerIdSpace() == PlayerIdSpace.ESPN) {
            espnIds.forEach(id -> poolIds.put(id, Math.toIntExact(id)));
            return poolIds;
        }
        Map<Long, Integer> crosswalk = crosswalk();
        for (Long id : espnIds) {
            Integer poolId = crosswalk.get(id);
            if (poolId != null) {
                poolIds.put(id, poolId);
            }
        }
        return poolIds;
    }

    private synchronized Map<Long, Integer> crosswalk() {
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return cached.poolIdByEspnId();
        }
        List<Candidate> espn = espnPool();
        List<Candidate> served = servedPool();
        PlayerIdMapping mapping = resolver.resolve(espn, served, Map.of());
        log.info("Built the ESPN id crosswalk for league drafts: {} of {} ESPN players matched "
                        + "against {} in the pool", mapping.matched(), espn.size(), served.size());
        cached = new Cached(Map.copyOf(mapping.nhlIdToPlatformId()), Instant.now().plus(ttl));
        return cached.poolIdByEspnId();
    }

    private List<Candidate> espnPool() {
        List<Candidate> candidates = new ArrayList<>();
        for (var skater : espnServiceClient.skaters(statsSeason)) {
            candidates.add(new Candidate(skater.getId(), skater.getFirstName() + " " + skater.getLastName(),
                    skater.getTeamAbbrev(), skater.getSweaterNumber()));
        }
        for (var goalie : espnServiceClient.goalies(statsSeason)) {
            candidates.add(new Candidate(goalie.getId(), goalie.getFirstName() + " " + goalie.getLastName(),
                    goalie.getTeamAbbrev(), goalie.getSweaterNumber()));
        }
        return candidates;
    }

    private List<Candidate> servedPool() {
        List<Candidate> candidates = new ArrayList<>();
        for (SkaterResponse skater : pool.getSkaters()) {
            candidates.add(new Candidate(skater.id(), skater.name(), skater.teamAbbrev(), skater.sweaterNumber()));
        }
        for (GoalieResponse goalie : pool.getGoalies()) {
            candidates.add(new Candidate(goalie.id(), goalie.name(), goalie.teamAbbrev(), goalie.sweaterNumber()));
        }
        return candidates;
    }
}
