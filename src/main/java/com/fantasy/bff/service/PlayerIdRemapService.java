package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.PlayerIdRemapReport;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.PlayerIdPair;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import com.fantasy.bff.service.mapping.PlayerIdResolver.Candidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rewrites the player ids in everything users have saved, from Yahoo's numbering to ESPN's.
 *
 * <p>Nobody publishes a crosswalk between the two, so it is built the same way the projection
 * mapping is: by matching identities. This is the only service that can see both pools — the
 * Yahoo one is still readable from yahoo-service's cache even though Yahoo itself no longer
 * serves players — and db-service, which owns the rows, does the writing.
 *
 * <p>It is a one-way move. Once the rows are ESPN's ids and the app is serving ESPN's pool,
 * going back means running the same exercise in reverse.
 */
@Service
public class PlayerIdRemapService {

    private static final Logger log = LoggerFactory.getLogger(PlayerIdRemapService.class);

    /**
     * Either pool coming back short means a truncated read, not a smaller league. Building a
     * crosswalk from half a pool would strand every row it didn't cover.
     */
    private static final int MINIMUM_CREDIBLE_POOL = 1000;

    /**
     * Measured coverage is around 98.7% (97.4% on the full name, 1.3% on the familiar-form
     * fallback). Well under that is a broken match rather than a thin pool, and applying it
     * would strand rows that a rerun cannot reach, because they are marked as migrated.
     */
    private static final double MINIMUM_COVERAGE_TO_APPLY = 0.90;

    /** Enough to see what kind of player went unmatched without answering with a directory. */
    private static final int UNMATCHED_SAMPLE = 50;

    private final PlayerServiceClient yahooPlayerClient;
    private final EspnServiceClient espnServiceClient;
    private final DatabaseServiceClient databaseServiceClient;
    private final PlayerIdResolver resolver;
    private final int statsSeason;

    public PlayerIdRemapService(PlayerServiceClient yahooPlayerClient,
                                EspnServiceClient espnServiceClient,
                                DatabaseServiceClient databaseServiceClient,
                                PlayerIdResolver resolver,
                                @Value("${services.espn-fantasy.stats-season}") int statsSeason) {
        this.yahooPlayerClient = yahooPlayerClient;
        this.espnServiceClient = espnServiceClient;
        this.databaseServiceClient = databaseServiceClient;
        this.resolver = resolver;
        this.statsSeason = statsSeason;
    }

    public PlayerIdRemapReport remap(boolean dryRun) {
        List<Candidate> yahoo = yahooPool();
        List<Candidate> espn = espnPool();
        requireCredible("Yahoo", yahoo.size());
        requireCredible("ESPN", espn.size());

        PlayerIdMapping mapping = resolver.resolve(yahoo, espn, Map.of());
        if (!dryRun && mapping.coverage() < MINIMUM_COVERAGE_TO_APPLY) {
            throw new IllegalStateException(("Only %.1f%% of the Yahoo pool matched an ESPN "
                    + "player, well under the %.0f%% a working match produces. Refusing to apply; "
                    + "run the dry run and look at what went unmatched.")
                    .formatted(mapping.coverage() * 100, MINIMUM_COVERAGE_TO_APPLY * 100));
        }

        List<PlayerIdPair> crosswalk = mapping.nhlIdToPlatformId().entrySet().stream()
                .map(entry -> new PlayerIdPair()
                        .from(Math.toIntExact(entry.getKey()))
                        .to(entry.getValue()))
                .toList();
        log.info("Remapping player ids ({}): {} Yahoo players, {} ESPN players, {} matched ({}%)",
                dryRun ? "dry run" : "applying", yahoo.size(), espn.size(), mapping.matched(),
                Math.round(mapping.coverage() * 100));

        return new PlayerIdRemapReport(
                yahoo.size(),
                espn.size(),
                mapping.matched(),
                mapping.matchedOnName(),
                mapping.matchedOnFallback(),
                mapping.unmatched().size(),
                mapping.coverage(),
                unmatchedSample(mapping),
                databaseServiceClient.remapPlayerIds(crosswalk, dryRun));
    }

    /**
     * Read straight from yahoo-service rather than through the configured pool source: the
     * crosswalk needs both sides whichever one the app is currently serving, and after the
     * switch the Yahoo side is no longer the pool.
     */
    private List<Candidate> yahooPool() {
        List<Candidate> candidates = new ArrayList<>();
        for (SkaterResponse skater : yahooPlayerClient.getSkaters()) {
            candidates.add(new Candidate(
                    skater.id(), skater.name(), skater.teamAbbrev(), skater.sweaterNumber()));
        }
        for (GoalieResponse goalie : yahooPlayerClient.getGoalies()) {
            candidates.add(new Candidate(
                    goalie.id(), goalie.name(), goalie.teamAbbrev(), goalie.sweaterNumber()));
        }
        return candidates;
    }

    private List<Candidate> espnPool() {
        List<Candidate> candidates = new ArrayList<>();
        for (var skater : espnServiceClient.skaters(statsSeason)) {
            candidates.add(new Candidate(skater.getId(),
                    skater.getFirstName() + " " + skater.getLastName(),
                    skater.getTeamAbbrev(), skater.getSweaterNumber()));
        }
        for (var goalie : espnServiceClient.goalies(statsSeason)) {
            candidates.add(new Candidate(goalie.getId(),
                    goalie.getFirstName() + " " + goalie.getLastName(),
                    goalie.getTeamAbbrev(), goalie.getSweaterNumber()));
        }
        return candidates;
    }

    private static void requireCredible(String platform, int players) {
        if (players < MINIMUM_CREDIBLE_POOL) {
            throw new IllegalStateException(
                    "The " + platform + " pool came back with only " + players + " players, far "
                            + "fewer than a league holds. Refusing to build a crosswalk from it.");
        }
    }

    private static List<String> unmatchedSample(PlayerIdMapping mapping) {
        return mapping.unmatched().stream()
                .limit(UNMATCHED_SAMPLE)
                .map(player -> player.team() == null || player.team().isBlank()
                        ? player.name()
                        : player.name() + " (" + player.team() + ")")
                .toList();
    }
}
