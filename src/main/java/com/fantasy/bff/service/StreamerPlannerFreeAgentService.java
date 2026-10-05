package com.fantasy.bff.service;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.CreaseGoalie;
import com.fantasy.bff.dto.response.CreaseNight;
import com.fantasy.bff.dto.response.FreeAgentListResponse;
import com.fantasy.bff.dto.response.FreeAgentResponse;
import com.fantasy.bff.dto.response.PlannerCrease;
import com.fantasy.bff.generated.projection.model.RangeGoalieResponse;
import com.fantasy.bff.generated.projection.model.RangeProjectionsResponse;
import com.fantasy.bff.generated.projection.model.RangeSkaterResponse;
import com.fantasy.bff.service.LeagueAvailablePlayers.Available;
import com.fantasy.bff.service.mapping.ModelStatMapping;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The streamer planner's second half: of the players a league has available, which ones the model
 * expects the most from over the chosen week.
 *
 * <p>The league's wire is read and joined to the model on identity by {@link LeagueAvailablePlayers}.
 *
 * <p>Nothing here scores the players. The stats go out in the projection's own vocabulary and the
 * client weighs them by the league's scoring settings, as it does everywhere else — a ranking
 * computed here could not be re-weighted, and every league weighs a shot block differently.
 */
@Service
public class StreamerPlannerFreeAgentService {

    private static final Logger log = LoggerFactory.getLogger(StreamerPlannerFreeAgentService.class);

    /** Projections asked of the model: the whole projected league, so no available player is missed. */
    private static final int PROJECTION_LIMIT = 2000;

    private final ProjectionServiceClient projectionServiceClient;
    private final LeagueAvailablePlayers leagueAvailablePlayers;
    private final StreamerPlannerAvailability availability;

    public StreamerPlannerFreeAgentService(
            ProjectionServiceClient projectionServiceClient,
            LeagueAvailablePlayers leagueAvailablePlayers,
            StreamerPlannerAvailability availability) {
        this.projectionServiceClient = projectionServiceClient;
        this.leagueAvailablePlayers = leagueAvailablePlayers;
        this.availability = availability;
    }

    public FreeAgentListResponse freeAgents(
            String userId, PlayerIdSpace platform, String leagueId, LocalDate start, LocalDate end) {
        availability.require();
        StreamerPlannerService.requireStretch(start, end);
        LeagueAvailablePlayers.Wire wire = leagueAvailablePlayers.read(userId, platform, leagueId);
        List<Available> available = wire.players();
        PlayerIdMapping mapping = wire.mapping();
        RangeProjectionsResponse projections =
                projectionServiceClient.rangeProjections(start, end, PROJECTION_LIMIT);

        List<FreeAgentResponse> rows = new ArrayList<>();
        for (RangeSkaterResponse skater : projections.getSkaters()) {
            Available player = wire.matched(skater.getNhlId());
            if (player != null && !player.goalie()) {
                rows.add(row(player, "skater", skater.getClubGames(), skater.getExpectedGames(),
                        skaterStats(skater, player.playsDefence())));
            }
        }
        for (RangeGoalieResponse goalie : projections.getGoalies()) {
            Available player = wire.matched(goalie.getNhlId());
            if (player != null && player.goalie()) {
                rows.add(row(player, "goalie", goalie.getClubGames(), goalie.getExpectedGames(),
                        goalieStats(goalie)));
            }
        }
        // A default order, not a ranking: expected games first, since a player who does not play
        // cannot help whatever the league scores. The client re-sorts once it has scored them.
        logUnranked(platform, available, rows, mapping, projections);
        rows.sort(Comparator.comparingDouble(FreeAgentResponse::expectedGames).reversed()
                .thenComparing(FreeAgentResponse::name));

        return new FreeAgentListResponse(
                start,
                end,
                projections.getSeason(),
                projections.getModelVersion(),
                List.copyOf(rows),
                creases(projections.getGoalies(), wire));
    }

    /**
     * The crease of every club with a goalie among the rows. Who starts a game is a question about
     * the whole crease: a free agent at 0.45 of a night is the likeliest starter beside a rostered
     * 0.40 and nobody's beside a rostered 0.55, so the rostered goalies go out too, unnamed. They
     * are the most starts over the stretch first, which is how the client breaks a tie.
     */
    private static List<PlannerCrease> creases(
            List<RangeGoalieResponse> goalies, LeagueAvailablePlayers.Wire wire) {
        Map<String, List<RangeGoalieResponse>> byClub = new TreeMap<>();
        for (RangeGoalieResponse goalie : goalies) {
            if (goalie.getTeam() != null) {
                byClub.computeIfAbsent(goalie.getTeam(), team -> new ArrayList<>()).add(goalie);
            }
        }
        Comparator<RangeGoalieResponse> mostStartsFirst = Comparator
                .comparingDouble((RangeGoalieResponse goalie) -> number(goalie.getExpectedGames()))
                .reversed()
                .thenComparing(RangeGoalieResponse::getNhlId, Comparator.nullsLast(Comparator.naturalOrder()));
        List<PlannerCrease> creases = new ArrayList<>();
        byClub.forEach((team, club) -> {
            List<CreaseGoalie> crease = club.stream()
                    .sorted(mostStartsFirst)
                    .map(goalie -> new CreaseGoalie(
                            listedGoalieId(wire, goalie.getNhlId()), nights(goalie)))
                    .toList();
            if (crease.stream().anyMatch(goalie -> goalie.playerId() != null)) {
                creases.add(new PlannerCrease(team, crease));
            }
        });
        return List.copyOf(creases);
    }

    /** His platform id when he is listed as a goalie, as {@link #freeAgents} lists him; else null. */
    private static String listedGoalieId(LeagueAvailablePlayers.Wire wire, Integer nhlId) {
        Available player = wire.matched(nhlId);
        return player != null && player.goalie() ? player.playerId() : null;
    }

    private static List<CreaseNight> nights(RangeGoalieResponse goalie) {
        return goalie.getNights().stream()
                .map(night -> new CreaseNight(night.getGameDate(), number(night.getShare())))
                .toList();
    }

    /**
     * Names the available players left out of the ranking, and why. The response no longer counts
     * them: the count lumped three different things together and gave the user nothing to act on.
     * Only one of the three is a fault — a player the model projects whose name did not match —
     * and that one is worth seeing here, since he vanishes from the ranking without a trace.
     */
    private static void logUnranked(
            PlayerIdSpace platform,
            List<Available> available,
            List<FreeAgentResponse> rows,
            PlayerIdMapping mapping,
            RangeProjectionsResponse projections) {
        if (rows.size() == available.size()) {
            return;
        }
        Map<String, Long> nhlIdByPlatformId = new HashMap<>();
        mapping.nhlIdToPlatformId().forEach((nhlId, platformId) ->
                nhlIdByPlatformId.put(String.valueOf(platformId), nhlId));
        Set<Long> projected = new HashSet<>();
        projections.getSkaters().forEach(line -> addId(projected, line.getNhlId()));
        projections.getGoalies().forEach(line -> addId(projected, line.getNhlId()));
        Set<String> ranked = new HashSet<>();
        rows.forEach(row -> ranked.add(row.playerId()));

        List<String> nameUnmatched = new ArrayList<>();
        List<String> notProjected = new ArrayList<>();
        List<String> typeDiffers = new ArrayList<>();
        for (Available player : available) {
            if (ranked.contains(player.playerId())) {
                continue;
            }
            Long nhlId = nhlIdByPlatformId.get(player.playerId());
            // The name and id are the platform's text, so a line break in either is stripped.
            String label =
                    (player.name() + " (" + player.playerId() + ")").replaceAll("[\r\n]", "");
            if (nhlId == null) {
                nameUnmatched.add(label);
            } else if (!projected.contains(nhlId)) {
                notProjected.add(label);
            } else {
                typeDiffers.add(label);
            }
        }
        log.info(
                "Streamer planner left {} of {} available {} players unranked: {} matched no NHL "
                        + "player {}, {} have no projection {}, {} are a skater on one side and a "
                        + "goalie on the other {}",
                available.size() - rows.size(),
                available.size(),
                platform,
                nameUnmatched.size(),
                nameUnmatched,
                notProjected.size(),
                notProjected,
                typeDiffers.size(),
                typeDiffers);
    }

    private static void addId(Set<Long> ids, Integer nhlId) {
        if (nhlId != null) {
            ids.add(nhlId.longValue());
        }
    }

    private static FreeAgentResponse row(
            Available player,
            String type,
            Integer clubGames,
            BigDecimal expectedGames,
            Map<String, Double> stats) {
        return new FreeAgentResponse(
                player.playerId(),
                player.name(),
                player.teamAbbrev(),
                type,
                player.positions(),
                player.availability(),
                clubGames == null ? 0 : clubGames,
                number(expectedGames),
                stats);
    }

    private static Map<String, Double> skaterStats(RangeSkaterResponse line, boolean playsDefence) {
        Map<String, Double> stats = new LinkedHashMap<>();
        put(stats, "goals", line.getGoals());
        put(stats, "assists", line.getAssists());
        put(stats, "points", line.getPoints());
        put(stats, "plusMinus", line.getPlusMinus());
        put(stats, "pim", line.getPim());
        put(stats, "ppg", line.getPpGoals());
        put(stats, "ppa", line.getPpAssists());
        put(stats, "ppp", line.getPpPoints());
        put(stats, "shg", line.getShGoals());
        put(stats, "sha", line.getShAssists());
        put(stats, "shp", line.getShPoints());
        put(stats, "gwg", line.getGwGoals());
        put(stats, "sog", line.getShots());
        put(stats, "hits", line.getHits());
        put(stats, "blocks", line.getBlocks());
        put(stats, "fw", line.getFaceoffsWon());
        put(stats, "fl", line.getFaceoffsLost());
        put(stats, "hatTricks", line.getHatTricks());
        put(stats, "shifts", line.getShifts());
        ModelStatMapping.putPercent(stats, "shPct", line.getShootingPct());
        put(stats, "toiPerGame", line.getToiPerGameSeconds());
        ModelStatMapping.putTotal(
                stats, "toi", line.getToiPerGameSeconds(), line.getExpectedGames());
        putSum(stats, "stpg", line.getPpGoals(), line.getShGoals());
        putSum(stats, "stpa", line.getPpAssists(), line.getShAssists());
        putSum(stats, "stp", line.getPpPoints(), line.getShPoints());
        if (playsDefence) {
            put(stats, "defPoints", line.getPoints());
        }
        return stats;
    }

    private static Map<String, Double> goalieStats(RangeGoalieResponse line) {
        Map<String, Double> stats = new LinkedHashMap<>();
        put(stats, "gs", line.getExpectedGames());
        put(stats, "w", line.getWins());
        put(stats, "l", line.getLosses());
        put(stats, "otl", line.getOtLosses());
        put(stats, "sho", line.getShutouts());
        put(stats, "sa", line.getShotsAgainst());
        put(stats, "sv", line.getSaves());
        put(stats, "ga", line.getGoalsAgainst());
        put(stats, "toi", line.getToiSeconds());
        ModelStatMapping.putWinPct(stats, line.getWins(), line.getLosses(), line.getOtLosses());
        // Rates, not totals: the season's own numbers, which is what the model projects.
        put(stats, "gaa", line.getGoalsAgainstAvg());
        put(stats, "svPct", line.getSavePct());
        return stats;
    }

    private static void put(Map<String, Double> stats, String key, BigDecimal value) {
        if (value != null) {
            stats.put(key, value.doubleValue());
        }
    }

    private static void putSum(Map<String, Double> stats, String key, BigDecimal a, BigDecimal b) {
        if (a != null || b != null) {
            stats.put(key, number(a) + number(b));
        }
    }

    private static double number(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }
}
