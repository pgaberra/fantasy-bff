package com.fantasy.bff.service;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.InjuriesResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.projection.model.AbsenceResponse;
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
     * @param injuries the current injury report, one entry per hurt player. Unlike everything
     *     else here this describes <b>today</b> rather than a season, so it goes stale with the
     *     cache: see the ttl this provider is configured with.
     * @param currentTeams the club each player is on today, keyed by platform id and spelled the
     *     way the platform spells it. Also a description of <b>today</b> rather than of a season.
     *     Only players the NHL side has a team for appear, so a missing entry means "no better
     *     answer than the pool's own", never "no team".
     */
    public record Context(
            PlayerIdMapping mapping,
            Map<Long, PlayerResponse> identities,
            Set<Integer> defenceEligible,
            Optional<Set<Integer>> rookies,
            List<InjuriesResponse.Injury> injuries,
            Map<Integer, String> currentTeams) {

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
    /**
     * When a cold build may be attempted again. Without it an unreachable upstream is retried on
     * every single request, and the player pool asks on each one now too — so the outage would
     * put a downstream timeout in front of the whole app rather than in front of one feature.
     */
    private final AtomicReference<Instant> coldRetryAt = new AtomicReference<>(Instant.MIN);

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
        if (cached == null && Instant.now().isBefore(coldRetryAt.get())) {
            // Already tried and failed a moment ago. Failing here costs the caller the same 502
            // it was going to get, without a second timeout to arrive at it.
            throw new IllegalStateException("The player id mapping is unavailable");
        }
        try {
            Context refreshed = load();
            cache.set(new Cached(refreshed, Instant.now().plus(ttl)));
            return refreshed;
        } catch (Exception e) {
            if (cached == null) {
                // Nothing to fall back to, so the caller has to hear about it — an empty mapping
                // would render as "no players were hot", which is a lie about the data.
                coldRetryAt.set(Instant.now().plus(RETRY_AFTER_FAILURE));
                throw new IllegalStateException("Failed to build the player id mapping", e);
            }
            log.error("Failed to refresh the player id mapping; serving the previous one", e);
            cache.set(new Cached(cached.context(), Instant.now().plus(RETRY_AFTER_FAILURE)));
            return cached.context();
        }
    }

    /**
     * The club each player is on today, keyed by platform id, or empty when the model cannot be
     * reached to say.
     *
     * <p>Empty rather than raising, which is the difference between this and {@link #context()}.
     * Who's hot has nothing to show without the mapping, so it is right for that to fail loudly.
     * The player pool is the app's spine and it already carries a team for every player — an
     * older one, from the platform's last sync. Losing the correction costs freshness; losing
     * the pool costs everything.
     */
    public Map<Integer, String> currentTeams() {
        try {
            return context().currentTeams();
        } catch (RuntimeException e) {
            log.error("Could not read current teams from the model; serving the pool's own", e);
            return Map.of();
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
        return new Context(
                mapping,
                identities,
                defenceEligible,
                rookies(nhlPlayers, mapping),
                injuries(nhlPlayers, mapping),
                currentTeams(nhlPlayers, mapping));
    }

    /**
     * Which club each player is on now, from the NHL side, keyed by platform id.
     *
     * <p>Note that the candidates above are built from the pool's own team and not from this.
     * The resolver uses team to break a tie between namesakes, and feeding it a team derived
     * from its own output would be circular — it would make "same team" true by construction
     * for exactly the players a tie break needs it to discriminate on.
     */
    private static Map<Integer, String> currentTeams(
            List<PlayerResponse> nhlPlayers, PlayerIdMapping mapping) {
        Map<Integer, String> teams = new HashMap<>();
        for (PlayerResponse player : nhlPlayers) {
            String team = PlayerIdResolver.platformTeam(player.getCurrentTeam());
            if (team == null || team.isBlank()) {
                continue;
            }
            Integer platformId = mapping.nhlIdToPlatformId().get(player.getNhlId().longValue());
            if (platformId != null) {
                teams.put(platformId, team);
            }
        }
        return teams;
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

    /**
     * The injury report, keyed by platform id. A player with no status is simply not on it, and
     * one the platform does not carry is dropped: there is no row in the app for him to mark.
     * There is no wholesale unknown here as there is for rookies — an empty report is a real
     * answer, and the "we could not ask" case is the whole context failing to load.
     *
     * <p>Status and date come from the projection service's {@code absence}, its one reading of
     * ESPN, Daily Faceoff and its injury register, and the date games are charged up to. ESPN's
     * report alone missed players who were out (Bedard in September 2026), so this passes the
     * service's resolution through rather than working one out here. The body part is ESPN's,
     * the only source that gives one.
     */
    private static List<InjuriesResponse.Injury> injuries(
            List<PlayerResponse> nhlPlayers, PlayerIdMapping mapping) {
        List<InjuriesResponse.Injury> injuries = new ArrayList<>();
        for (PlayerResponse player : nhlPlayers) {
            Integer platformId = mapping.nhlIdToPlatformId().get(player.getNhlId().longValue());
            if (platformId == null) {
                continue;
            }
            AbsenceResponse absence = player.getAbsence();
            if (absence != null && absence.getStatus() != null) {
                injuries.add(new InjuriesResponse.Injury(
                        platformId,
                        absence.getStatus(),
                        player.getInjuryBodyPart(),
                        absence.getBackOn()));
            } else if (absence == null && player.getInjuryStatus() != null) {
                // A projection service from before `absence` was served: ESPN's report alone.
                injuries.add(new InjuriesResponse.Injury(
                        platformId,
                        player.getInjuryStatus(),
                        player.getInjuryBodyPart(),
                        player.getInjuryExpectedReturn()));
            }
        }
        return injuries;
    }
}
