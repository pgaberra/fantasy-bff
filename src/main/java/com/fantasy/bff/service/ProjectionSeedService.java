package com.fantasy.bff.service;

import com.fantasy.bff.client.PlayerServiceClient;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final PlayerServiceClient playerServiceClient;
    private final PlayerIdResolver resolver;
    private final PlayerIdOverrides overrides;

    public ProjectionSeedService(
            ProjectionServiceClient projectionServiceClient,
            PlayerServiceClient playerServiceClient,
            PlayerIdResolver resolver,
            PlayerIdOverrides overrides) {
        this.projectionServiceClient = projectionServiceClient;
        this.playerServiceClient = playerServiceClient;
        this.resolver = resolver;
        this.overrides = overrides;
    }

    /**
     * @param players the seeded lines, keyed by platform player id
     * @param skatersSeeded how many skaters made it through
     * @param goaliesSeeded how many goalies made it through
     * @param unmapped players the model projected that the platform doesn't carry
     * @param withoutWorkload goalies the model projects no starts for, left out on purpose
     */
    public record Seed(
            List<PlayerProjection> players,
            int skatersSeeded,
            int goaliesSeeded,
            int unmapped,
            int withoutWorkload) {}

    public Seed seed(int season, String modelVersion) {
        List<PlayerResponse> nhlPlayers = projectionServiceClient.activePlayers();
        Map<Long, PlayerResponse> byNhlId = nhlPlayers.stream()
                .collect(Collectors.toMap(p -> p.getNhlId().longValue(), Function.identity(), (a, b) -> a));

        PlayerIdMapping mapping = resolver.resolve(
                nhlPlayers.stream().map(ProjectionSeedService::nhlCandidate).toList(),
                platformCandidates(),
                overrides.asMap());

        List<PlayerProjection> seeded = new ArrayList<>();
        int unmapped = 0;
        int withoutWorkload = 0;

        for (SkaterProjectionResponse projection : projectionServiceClient.skaterProjections(season, modelVersion)) {
            Integer platformId = mapping.nhlIdToPlatformId().get(projection.getNhlId().longValue());
            if (platformId == null) {
                unmapped++;
                continue;
            }
            seeded.add(skaterLine(platformId, projection));
        }
        int skaters = seeded.size();

        for (GoalieProjectionResponse projection : projectionServiceClient.goalieProjections(season, modelVersion)) {
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

        Seed seed = new Seed(seeded, skaters, seeded.size() - skaters, unmapped, withoutWorkload);
        log.info(
                "Seeded {} projections for {} ({}): {} skaters, {} goalies; {} unmapped, "
                        + "{} goalies without a projected workload. Player pool: {}",
                seed.players().size(),
                season,
                forLog(modelVersion),
                seed.skatersSeeded(),
                seed.goaliesSeeded(),
                seed.unmapped(),
                seed.withoutWorkload(),
                byNhlId.size());
        return seed;
    }

    private List<Candidate> platformCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        for (SkaterResponse skater : playerServiceClient.getSkaters()) {
            candidates.add(new Candidate(
                    skater.id(), skater.name(), skater.teamAbbrev(), skater.sweaterNumber()));
        }
        for (GoalieResponse goalie : playerServiceClient.getGoalies()) {
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
    private static String forLog(String value) {
        return value == null ? "" : value.replaceAll("[\r\n]", "");
    }

    private static BigDecimal scale(BigDecimal value, int factor) {
        return value == null ? null : value.multiply(BigDecimal.valueOf(factor));
    }
}
