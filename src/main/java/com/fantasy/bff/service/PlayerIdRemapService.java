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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites the player ids in everything users have saved, from one platform's numbering to the
 * other's.
 *
 * <p>Nobody publishes a crosswalk between the two, so it is built the same way the projection
 * mapping is: by matching identities. This is the only service that can see both pools, and
 * db-service, which owns the rows, does the writing.
 *
 * <p>It runs in either direction. The first move went from Yahoo's ids to ESPN's when Yahoo
 * stopped serving its players; when Yahoo served them again the pool went back. Each direction
 * leaves the app serving the pool it moved to, so it goes with switching {@code players.source}.
 *
 * <p>The call to db-service rewrites every stored projection in one request and runs for as
 * long as that takes, so it goes out on the migration client's timeout rather than the one
 * sized for requests a user is waiting on. If it is cut off anyway, db-service does not know
 * the caller has gone and commits regardless — so a failed apply is not the same as no apply.
 * A dry run afterwards says which happened: it reports the projections still on the old ids,
 * and that is zero once the write has landed.
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
     * The gate is coverage of the players who <b>actually played</b>, not of the whole pool.
     *
     * <p>Measured against staging when moving to ESPN: 83.7% of the Yahoo pool found an ESPN
     * counterpart, and every single one of the 259 that didn't had played no games. That is
     * structural rather than wrong — each platform lists its own fringe of players who never got
     * into a game. Gating on the whole pool would have blocked a perfectly good crosswalk.
     *
     * <p>A player who did play is a different matter: they are the ones a projection has real
     * numbers for, and losing them is what a broken match looks like. A little headroom under
     * 100% covers someone who played a handful of games and then left the league. Games are
     * counted on the side being left, since those are the players the stored rows name.
     */
    private static final double MINIMUM_PLAYED_COVERAGE_TO_APPLY = 0.95;

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

    /**
     * @param to the numbering to move the stored rows into; they are read in the other one
     */
    public PlayerIdRemapReport remap(boolean dryRun, PlayerIdSpace to) {
        PlayerIdSpace from = to == PlayerIdSpace.ESPN ? PlayerIdSpace.YAHOO : PlayerIdSpace.ESPN;
        Map<Long, Integer> yahooGames = new LinkedHashMap<>();
        Map<Long, Integer> espnGames = new LinkedHashMap<>();
        List<Candidate> yahoo = yahooPool(yahooGames);
        List<Candidate> espn = espnPool(espnGames);
        requireCredible("Yahoo", yahoo.size());
        requireCredible("ESPN", espn.size());

        List<Candidate> leaving = from == PlayerIdSpace.YAHOO ? yahoo : espn;
        List<Candidate> arriving = from == PlayerIdSpace.YAHOO ? espn : yahoo;
        Map<Long, Integer> gamesPlayed = from == PlayerIdSpace.YAHOO ? yahooGames : espnGames;

        PlayerIdMapping mapping = resolver.resolve(leaving, arriving, Map.of());
        int played = (int) gamesPlayed.values().stream().filter(games -> games > 0).count();
        int matchedWhoPlayed = (int) mapping.nhlIdToPlatformId().keySet().stream()
                .filter(id -> gamesPlayed.getOrDefault(id, 0) > 0)
                .count();
        double playedCoverage = played == 0 ? 0.0 : (double) matchedWhoPlayed / played;
        if (!dryRun && playedCoverage < MINIMUM_PLAYED_COVERAGE_TO_APPLY) {
            throw new IllegalStateException(("Only %.1f%% of the %s players who actually "
                    + "played a game found a %s counterpart, under the %.0f%% a working match "
                    + "produces. Refusing to apply; run the dry run and look at what went "
                    + "unmatched.")
                    .formatted(playedCoverage * 100, label(from), label(to),
                            MINIMUM_PLAYED_COVERAGE_TO_APPLY * 100));
        }

        List<PlayerIdPair> crosswalk = mapping.nhlIdToPlatformId().entrySet().stream()
                .map(entry -> new PlayerIdPair()
                        .from(Math.toIntExact(entry.getKey()))
                        .to(entry.getValue()))
                .toList();
        log.info("Remapping player ids to {} ({}): {} Yahoo players, {} ESPN players, {} matched "
                        + "({}% of the pool being left, {}% of the {} who played)",
                to == PlayerIdSpace.ESPN ? "ESPN" : "Yahoo", dryRun ? "dry run" : "applying",
                yahoo.size(), espn.size(), mapping.matched(), Math.round(mapping.coverage() * 100),
                Math.round(playedCoverage * 100), played);

        return new PlayerIdRemapReport(
                from,
                to,
                yahoo.size(),
                espn.size(),
                mapping.matched(),
                mapping.matchedOnName(),
                mapping.matchedOnFallback(),
                mapping.unmatched().size(),
                mapping.coverage(),
                played,
                matchedWhoPlayed,
                playedCoverage,
                unmatchedSample(mapping, gamesPlayed),
                databaseServiceClient.remapPlayerIds(crosswalk, dryRun, from, to));
    }

    /**
     * Read straight from yahoo-service rather than through the configured pool source: the
     * crosswalk needs both sides whichever one the app is currently serving.
     */
    private List<Candidate> yahooPool(Map<Long, Integer> gamesPlayed) {
        List<Candidate> candidates = new ArrayList<>();
        for (SkaterResponse skater : yahooPlayerClient.getSkaters()) {
            candidates.add(new Candidate(
                    skater.id(), skater.name(), skater.teamAbbrev(), skater.sweaterNumber()));
            gamesPlayed.put((long) skater.id(), skater.stats().utility().gp());
        }
        for (GoalieResponse goalie : yahooPlayerClient.getGoalies()) {
            candidates.add(new Candidate(
                    goalie.id(), goalie.name(), goalie.teamAbbrev(), goalie.sweaterNumber()));
            gamesPlayed.put((long) goalie.id(), goalie.stats().utility().gp());
        }
        return candidates;
    }

    private List<Candidate> espnPool(Map<Long, Integer> gamesPlayed) {
        List<Candidate> candidates = new ArrayList<>();
        for (var skater : espnServiceClient.skaters(statsSeason)) {
            candidates.add(new Candidate(skater.getId(),
                    skater.getFirstName() + " " + skater.getLastName(),
                    skater.getTeamAbbrev(), skater.getSweaterNumber()));
            gamesPlayed.put(skater.getId(), orZero(skater.getGamesPlayed()));
        }
        for (var goalie : espnServiceClient.goalies(statsSeason)) {
            candidates.add(new Candidate(goalie.getId(),
                    goalie.getFirstName() + " " + goalie.getLastName(),
                    goalie.getTeamAbbrev(), goalie.getSweaterNumber()));
            gamesPlayed.put(goalie.getId(), orZero(goalie.getGamesPlayed()));
        }
        return candidates;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String label(PlayerIdSpace space) {
        return space == PlayerIdSpace.ESPN ? "ESPN" : "Yahoo";
    }

    private static void requireCredible(String platform, int players) {
        if (players < MINIMUM_CREDIBLE_POOL) {
            throw new IllegalStateException(
                    "The " + platform + " pool came back with only " + players + " players, far "
                            + "fewer than a league holds. Refusing to build a crosswalk from it.");
        }
    }

    /**
     * The unmatched, busiest first and carrying the games they played, because a player with a
     * season behind them is the one worth looking at — an alphabetical list of players who never
     * got into a game says nothing.
     */
    private static List<String> unmatchedSample(PlayerIdMapping mapping, Map<Long, Integer> gamesPlayed) {
        return mapping.unmatched().stream()
                .sorted(Comparator.comparingInt(
                        (PlayerIdMapping.Unmatched player) -> gamesPlayed.getOrDefault(player.nhlId(), 0))
                        .reversed())
                .limit(UNMATCHED_SAMPLE)
                .map(player -> "%s (%s, %d GP)".formatted(
                        player.name(),
                        player.team() == null || player.team().isBlank() ? "no team" : player.team(),
                        gamesPlayed.getOrDefault(player.nhlId(), 0)))
                .toList();
    }
}
