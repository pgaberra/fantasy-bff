package com.fantasy.bff.service;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.projection.model.GoalieProjectionResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import com.fantasy.bff.service.mapping.PlayerIdOverrides;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import com.fantasy.bff.service.mapping.PlayerIdResolver.Candidate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns the projection service's model output into a projection a user can save and edit.
 *
 * <p>Three things have to line up. The projections are keyed by NHL id, the app is keyed by the
 * platform's player id, so {@link PlayerIdResolver} bridges them. The two sides also name the
 * same stats differently, so the vocabularies are translated here. And a stat the projection
 * service leaves out — a goalie with no projected starts has no save % — must not be filled in
 * with a zero, which would read as a terrible goalie rather than an absent one.
 */
@Service
public class ProjectionSeedService {

    private static final Logger log = LoggerFactory.getLogger(ProjectionSeedService.class);

    private final ProjectionServiceClient projectionServiceClient;
    private final PlayerPoolSource playerPool;
    private final PlayerIdResolver resolver;
    private final PlayerIdOverrides overrides;
    private final ProjectionSeedCache cache;

    public ProjectionSeedService(
            ProjectionServiceClient projectionServiceClient,
            PlayerPoolSource playerPool,
            PlayerIdResolver resolver,
            PlayerIdOverrides overrides,
            ProjectionSeedCache cache) {
        this.projectionServiceClient = projectionServiceClient;
        this.playerPool = playerPool;
        this.resolver = resolver;
        this.overrides = overrides;
        this.cache = cache;
    }

    /**
     * @param players the seeded lines, keyed by platform player id
     * @param modelVersion the version the rows actually came back stamped with, which is not
     *     necessarily the one asked for: with nothing pinned, projection-service picks the
     *     season's most recent run and says so on every row
     * @param skatersSeeded how many skaters made it through
     * @param goaliesSeeded how many goalies made it through
     * @param unmapped players the model projected that the platform doesn't carry
     * @param withoutWorkload goalies the model projects no starts for, left out on purpose
     * @param retiredZeroed pool players who have left the league, seeded at zero
     */
    public record Seed(
            List<PlayerProjection> players,
            String modelVersion,
            int skatersSeeded,
            int goaliesSeeded,
            int unmapped,
            int withoutWorkload,
            int retiredZeroed) {}

    public Seed seed(int season, String modelVersion) {
        return seed(season, modelVersion, null, null);
    }

    /**
     * The model's lines, optionally only the top of them.
     *
     * <p>The limits trim the rows returned and nothing else: the counts on the {@link Seed} keep
     * reporting the whole of what the model reached, because a caller showing five rows still
     * wants to say how much of the league it covers. Nor do they save any work — the seed is
     * computed in full either way, since the coverage counts and the retired-zeroing both need
     * the whole board. What they save is sending it: a caller drawing a five-row preview would
     * otherwise download every line to show four of them.
     *
     * <p>Which is why the full board is what {@link ProjectionSeedCache} holds, and the limits
     * are applied to what comes back: the preview and the projection it seeds are the same
     * board, so they share one entry. The rows in it are shared with every other reader and
     * must not be modified — the list is unmodifiable, the {@code PlayerProjection}s in it are
     * only read and serialised.
     *
     * <p>Ordered the way the player pool is, skaters by projected points and goalies by projected
     * wins, so the top of this board is the top of that one. A caller scoring by its own weights
     * will not want exactly these players in exactly this order — ask for enough of them that the
     * ones it would pick are inside.
     *
     * @param skaterLimit how many skater lines to return, or null for all of them
     * @param goalieLimit how many goalie lines to return, or null for all of them
     */
    public Seed seed(int season, String modelVersion, Integer skaterLimit, Integer goalieLimit) {
        Seed whole = cache.get(season, modelVersion).orElseGet(() -> {
            Seed built = build(season, modelVersion);
            cache.put(season, modelVersion, built);
            return built;
        });
        if (skaterLimit == null && goalieLimit == null) {
            return whole;
        }
        return new Seed(
                topOf(whole.players(), skaterLimit, goalieLimit),
                whole.modelVersion(),
                whole.skatersSeeded(),
                whole.goaliesSeeded(),
                whole.unmapped(),
                whole.withoutWorkload(),
                whole.retiredZeroed());
    }

    /**
     * The whole board, built from the model. Called on a cache miss and nowhere else, so its log
     * line marks a rebuild rather than a read.
     */
    private Seed build(int season, String modelVersion) {
        List<PlayerResponse> nhlPlayers = projectionServiceClient.activePlayers(null);
        Map<Long, PlayerResponse> byNhlId = nhlPlayers.stream()
                .collect(Collectors.toMap(p -> p.getNhlId().longValue(), Function.identity(), (a, b) -> a));

        List<Candidate> pool = platformCandidates();
        PlayerIdMapping mapping = resolver.resolve(
                nhlPlayers.stream().map(ProjectionSeedService::nhlCandidate).toList(),
                pool,
                overrides.asMap());

        List<PlayerProjection> seeded = new ArrayList<>();
        int unmapped = 0;
        int withoutWorkload = 0;
        // What we asked for, until a row tells us otherwise. Unpinned, the version is the
        // service's to choose, and the only honest way to report it is to read it off what came
        // back rather than to echo the request.
        String servedVersion = modelVersion;

        for (SkaterProjectionResponse projection : projectionServiceClient.skaterProjections(season, modelVersion)) {
            servedVersion = stampedOr(projection.getModelVersion(), servedVersion);
            Integer platformId = mapping.nhlIdToPlatformId().get(projection.getNhlId().longValue());
            if (platformId == null) {
                unmapped++;
                continue;
            }
            seeded.add(skaterLine(platformId, projection));
        }
        int skaters = seeded.size();

        for (GoalieProjectionResponse projection : projectionServiceClient.goalieProjections(season, modelVersion)) {
            servedVersion = stampedOr(projection.getModelVersion(), servedVersion);
            Integer platformId = mapping.nhlIdToPlatformId().get(projection.getNhlId().longValue());
            if (platformId == null) {
                unmapped++;
                continue;
            }
            // No starts means no rate stats. Seeding a .000 save % would read as the worst
            // goalie in the league rather than one the model expects not to play.
            if (projection.getSavePct() == null || projection.getGamesStarted() == null) {
                withoutWorkload++;
                continue;
            }
            seeded.add(goalieLine(platformId, projection));
        }

        int goalies = seeded.size() - skaters;
        int retiredZeroed = seedRetired(seeded, pool);

        // Unmodifiable because it is about to be shared with every reader of this season's seed.
        Seed seed = new Seed(
                List.copyOf(seeded),
                servedVersion,
                skaters,
                goalies,
                unmapped,
                withoutWorkload,
                retiredZeroed);
        log.info(
                "Seeded {} projections for {} ({}): {} skaters, {} goalies; {} unmapped, "
                        + "{} goalies without a projected workload, {} retired zeroed. "
                        + "Player pool: {}",
                seeded.size(),
                season,
                forLog(servedVersion),
                seed.skatersSeeded(),
                seed.goaliesSeeded(),
                seed.unmapped(),
                seed.withoutWorkload(),
                seed.retiredZeroed(),
                byNhlId.size());
        return seed;
    }

    /**
     * The highest {@code skaterLimit} skaters and {@code goalieLimit} goalies, by the stat each
     * kind is ranked on elsewhere. A null limit takes them all; a list with neither limit set is
     * returned untouched, in the order it was built.
     */
    private static List<PlayerProjection> topOf(
            List<PlayerProjection> seeded, Integer skaterLimit, Integer goalieLimit) {
        if (skaterLimit == null && goalieLimit == null) {
            return seeded;
        }
        List<PlayerProjection> top = new ArrayList<>();
        top.addAll(top(seeded, PlayerProjection.TypeEnum.SKATER, "points", skaterLimit));
        top.addAll(top(seeded, PlayerProjection.TypeEnum.GOALIE, "w", goalieLimit));
        return top;
    }

    private static List<PlayerProjection> top(
            List<PlayerProjection> seeded,
            PlayerProjection.TypeEnum type,
            String rankedOn,
            Integer limit) {
        Stream<PlayerProjection> ofType =
                seeded.stream().filter(projection -> projection.getType() == type);
        if (limit == null) {
            return ofType.toList();
        }
        return ofType.sorted(Comparator.comparingDouble(
                        (PlayerProjection projection) ->
                                projection.getStats().getScoring().getOrDefault(rankedOn, 0d))
                .reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Seeds a zero line for every pool player who has left the league.
     *
     * <p>A platform keeps carrying a player for a while after they retire, and the model does
     * not project them, so they would otherwise arrive as an untouched row — indistinguishable
     * from a player the model simply could not reach. Zero is the honest number: they will not
     * play. A prospect is the opposite case and must not be caught here, which is why this
     * matches against the store's inactive players rather than against whoever went unmapped:
     * a prospect who has never played an NHL game is not in the store at all.
     *
     * <p>Matching runs only over pool players nothing has claimed yet, so an active player who
     * shares a name with a retired one keeps his projection. That ordering is the safeguard —
     * there are two Sebastian Ahos, and only one of them has retired.
     *
     * @return how many rows were zeroed
     */
    private int seedRetired(List<PlayerProjection> seeded, List<Candidate> pool) {
        Set<Integer> claimed =
                seeded.stream().map(PlayerProjection::getPlayerId).collect(Collectors.toSet());
        List<Candidate> unclaimed =
                pool.stream().filter(c -> !claimed.contains((int) c.id())).toList();
        if (unclaimed.isEmpty()) {
            return 0;
        }

        List<Candidate> retired =
                projectionServiceClient.retiredPlayers().stream()
                        .map(ProjectionSeedService::nhlCandidate)
                        .toList();
        // No overrides here: they pin a player to his projection, and pinning one to a zero
        // line would be a way to silently delete him.
        PlayerIdMapping retiredMapping = resolver.resolve(retired, unclaimed, Map.of());

        Set<Integer> goalieIds = goaliePlatformIds();
        for (Integer platformId : retiredMapping.nhlIdToPlatformId().values()) {
            seeded.add(
                    goalieIds.contains(platformId)
                            ? zeroLine(platformId, PlayerProjection.TypeEnum.GOALIE, GOALIE_STATS)
                            : zeroLine(platformId, PlayerProjection.TypeEnum.SKATER, SKATER_STATS));
        }
        return retiredMapping.matched();
    }

    private Set<Integer> goaliePlatformIds() {
        return playerPool.getGoalies().stream().map(GoalieResponse::id).collect(Collectors.toSet());
    }

    private PlayerProjection zeroLine(
            int platformId, PlayerProjection.TypeEnum type, Stats stats) {
        Map<String, Double> utility = new HashMap<>();
        for (String key : stats.utility()) {
            utility.put(key, 0.0);
        }
        Map<String, Double> scoring = new HashMap<>();
        for (String key : stats.scoring()) {
            scoring.put(key, 0.0);
        }
        return line(platformId, type, utility, scoring);
    }

    /** The stat keys a zero line has to fill, so the row is complete rather than half-blank. */
    private record Stats(List<String> utility, List<String> scoring) {}

    private static final Stats SKATER_STATS =
            new Stats(
                    List.of("gp", "toiPerGame"),
                    List.of(
                            "goals", "assists", "points", "plusMinus", "pim", "ppg", "ppa", "ppp",
                            "shg", "sha", "shp", "gwg", "sog", "shPct", "fw", "fl", "hits",
                            "blocks"));

    private static final Stats GOALIE_STATS =
            new Stats(
                    List.of("gp"),
                    List.of("gs", "w", "l", "sho", "sa", "sv", "ga", "gaa", "svPct"));

    private List<Candidate> platformCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        for (SkaterResponse skater : playerPool.getSkaters()) {
            candidates.add(new Candidate(
                    skater.id(), skater.name(), skater.teamAbbrev(), skater.sweaterNumber()));
        }
        for (GoalieResponse goalie : playerPool.getGoalies()) {
            candidates.add(new Candidate(
                    goalie.id(), goalie.name(), goalie.teamAbbrev(), goalie.sweaterNumber()));
        }
        return candidates;
    }

    private static Candidate nhlCandidate(PlayerResponse player) {
        return new Candidate(
                player.getNhlId().longValue(),
                player.getFullName(),
                player.getCurrentTeam(),
                player.getSweaterNumber());
    }

    private PlayerProjection skaterLine(int platformId, SkaterProjectionResponse p) {
        Map<String, Double> utility = new HashMap<>();
        put(utility, "gp", p.getGamesPlayed());
        put(utility, "toiPerGame", p.getToiPerGameSeconds());

        Map<String, Double> scoring = new HashMap<>();
        put(scoring, "goals", p.getGoals());
        put(scoring, "assists", p.getAssists());
        put(scoring, "points", p.getPoints());
        put(scoring, "plusMinus", p.getPlusMinus());
        put(scoring, "pim", p.getPim());
        put(scoring, "ppg", p.getPpGoals());
        put(scoring, "ppa", p.getPpAssists());
        put(scoring, "ppp", p.getPpPoints());
        put(scoring, "shg", p.getShGoals());
        put(scoring, "sha", p.getShAssists());
        put(scoring, "shp", p.getShPoints());
        put(scoring, "gwg", p.getGwGoals());
        put(scoring, "hatTricks", p.getHatTricks());
        put(scoring, "sog", p.getShots());
        // The model works in a fraction; the app's column is a percentage.
        put(scoring, "shPct", scale(p.getShootingPct(), 100));
        put(scoring, "fw", p.getFaceoffsWon());
        put(scoring, "fl", p.getFaceoffsLost());
        put(scoring, "hits", p.getHits());
        put(scoring, "blocks", p.getBlocks());

        return line(platformId, PlayerProjection.TypeEnum.SKATER, utility, scoring);
    }

    private PlayerProjection goalieLine(int platformId, GoalieProjectionResponse p) {
        Map<String, Double> utility = new HashMap<>();
        put(utility, "gp", p.getGamesPlayed());

        Map<String, Double> scoring = new HashMap<>();
        put(scoring, "gs", p.getGamesStarted());
        put(scoring, "w", p.getWins());
        put(scoring, "l", p.getLosses());
        put(scoring, "sho", p.getShutouts());
        put(scoring, "sa", p.getShotsAgainst());
        put(scoring, "sv", p.getSaves());
        put(scoring, "ga", p.getGoalsAgainst());
        put(scoring, "gaa", p.getGoalsAgainstAvg());
        // Save % stays a fraction here, matching the app's goalie column.
        put(scoring, "svPct", p.getSavePct());

        return line(platformId, PlayerProjection.TypeEnum.GOALIE, utility, scoring);
    }

    private PlayerProjection line(
            int platformId,
            PlayerProjection.TypeEnum type,
            Map<String, Double> utility,
            Map<String, Double> scoring) {
        PlayerStats stats = new PlayerStats();
        stats.setUtility(utility);
        stats.setScoring(scoring);

        PlayerProjection projection = new PlayerProjection();
        projection.setPlayerId(platformId);
        projection.setType(type);
        projection.setStats(stats);
        return projection;
    }

    /**
     * The two services' specs generate different numeric types — the projection service's
     * plain {@code number} becomes a BigDecimal, db-service's {@code number/double} a Double —
     * so the conversion happens once, here. A missing value is left out rather than zeroed.
     */
    private static void put(Map<String, Double> target, String key, BigDecimal value) {
        if (value != null) {
            target.put(key, value.doubleValue());
        }
    }

    /**
     * The model version reaches us from the caller, so it is stripped of line breaks before it
     * reaches the log — otherwise a crafted value could forge log lines around it.
     */
    /** The version a row was stamped with, keeping what we had if a row does not carry one. */
    private static String stampedOr(String stamped, String fallback) {
        return stamped == null || stamped.isBlank() ? fallback : stamped;
    }

    private static String forLog(String value) {
        return value == null ? "" : value.replaceAll("[\r\n]", "");
    }

    private static BigDecimal scale(BigDecimal value, int factor) {
        return value == null ? null : value.multiply(BigDecimal.valueOf(factor));
    }
}
