package com.fantasy.bff.service;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.PlannerRosterPlayer;
import com.fantasy.bff.dto.response.ScoutLine;
import com.fantasy.bff.dto.response.ScoutListResponse;
import com.fantasy.bff.dto.response.ScoutMyTeamResponse;
import com.fantasy.bff.dto.response.ScoutPlayerResponse;
import com.fantasy.bff.dto.response.ScoutRosterPlayer;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.generated.projection.model.GoalieProjectionResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonGoalieResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonSkaterResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.service.LeagueAvailablePlayers.Available;
import com.fantasy.bff.service.mapping.ModelStatMapping;
import com.fantasy.bff.service.mapping.PlayerFieldMapping;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The FA scout: of the players a league has available, the ones worth keeping for the rest of the
 * season. Where the streamer planner asks who helps over the next few nights, this asks who the
 * league would draft if it drafted again today: a player traded into a bigger role, or one handed
 * more ice and the first power play than anyone expected in September.
 *
 * <p>Each player goes out with two of the model's lines: the rest of the season as it is served
 * everywhere else (lifted to the season line's scale, the season so far left out), and his line
 * as it stood on the eve of the season, frozen as {@code model_version=preseason}. Nothing is
 * scored here, as in the planner: the client ranks both lines by the league's own settings, and
 * the distance between the two ranks is what says a player has risen.
 *
 * <p>The user's own team goes out on the same rest-of-season line ({@link #myTeam}), so the client
 * can weigh each pickup against the player it would replace: a pickup needs a roster spot, and the
 * one to give up is the player whose loss costs the team least.
 */
@Service
public class FaScoutService {

    /** The model version projection-service freezes the eve of the season under. */
    static final String PRESEASON = "preseason";

    private final ProjectionServiceClient projectionServiceClient;
    private final LeagueAvailablePlayers leagueAvailablePlayers;
    private final LeagueOwnTeam ownTeam;
    private final NhlIdentityJoin identityJoin;
    private final FaScoutAvailability availability;
    private final int season;

    public FaScoutService(
            ProjectionServiceClient projectionServiceClient,
            LeagueAvailablePlayers leagueAvailablePlayers,
            LeagueOwnTeam ownTeam,
            NhlIdentityJoin identityJoin,
            FaScoutAvailability availability,
            @Value("${services.projection.season}") int season) {
        this.projectionServiceClient = projectionServiceClient;
        this.leagueAvailablePlayers = leagueAvailablePlayers;
        this.ownTeam = ownTeam;
        this.identityJoin = identityJoin;
        this.availability = availability;
        this.season = season;
    }

    public ScoutListResponse freeAgents(String userId, PlayerIdSpace platform, String leagueId) {
        availability.require();
        List<RestOfSeasonSkaterResponse> skaters = projectionServiceClient.restOfSeasonSkaters(season);
        List<RestOfSeasonGoalieResponse> goalies = projectionServiceClient.restOfSeasonGoalies(season);
        if (skaters.isEmpty() && goalies.isEmpty()) {
            // Before the first game or after the last: no rest of the season, so the league's wire
            // is not worth a call to the platform.
            return new ScoutListResponse(season, null, false, false, List.of());
        }

        LeagueAvailablePlayers.Wire wire = leagueAvailablePlayers.read(userId, platform, leagueId);
        Map<Integer, SkaterProjectionResponse> preseasonSkaters = byNhlId(
                projectionServiceClient.skaterProjections(season, PRESEASON),
                SkaterProjectionResponse::getNhlId);
        Map<Integer, GoalieProjectionResponse> preseasonGoalies = byNhlId(
                projectionServiceClient.goalieProjections(season, PRESEASON),
                GoalieProjectionResponse::getNhlId);

        List<ScoutPlayerResponse> rows = new ArrayList<>();
        for (RestOfSeasonSkaterResponse row : skaters) {
            Available player = wire.matched(row.getNhlId());
            if (player != null && !player.goalie()) {
                SkaterProjectionResponse before = preseasonSkaters.get(row.getNhlId());
                rows.add(row(player, "skater",
                        skaterLine(ProjectionSeedService.seasonShape(row), player.playsDefence()),
                        before == null ? null : skaterLine(before, player.playsDefence())));
            }
        }
        for (RestOfSeasonGoalieResponse row : goalies) {
            Available player = wire.matched(row.getNhlId());
            if (player != null && player.goalie()) {
                GoalieProjectionResponse before = preseasonGoalies.get(row.getNhlId());
                rows.add(row(player, "goalie",
                        goalieLine(ProjectionSeedService.seasonShape(row)),
                        before == null ? null : goalieLine(before)));
            }
        }
        // A default order, not a ranking: the client scores them by the league's settings.
        rows.sort(Comparator.comparingDouble((ScoutPlayerResponse row) -> row.restOfSeason().games())
                .reversed()
                .thenComparing(ScoutPlayerResponse::name));

        String modelVersion = !skaters.isEmpty()
                ? skaters.getFirst().getModelVersion()
                : goalies.getFirst().getModelVersion();
        return new ScoutListResponse(
                season,
                modelVersion,
                true,
                !preseasonSkaters.isEmpty() || !preseasonGoalies.isEmpty(),
                List.copyOf(rows));
    }

    /**
     * The user's own team in the league, each player with the model's rest of the season on the
     * line the available players carry, so the two can be scored together. A player the model has
     * no line for goes out without one.
     */
    public ScoutMyTeamResponse myTeam(String userId, PlayerIdSpace platform, String leagueId) {
        availability.require();
        LeagueOwnTeam.Team team = ownTeam.read(userId, platform, leagueId);
        if (!team.found()) {
            return new ScoutMyTeamResponse(false, null, List.of());
        }
        Map<String, Long> nhlIds = new HashMap<>();
        identityJoin.join(team.players().stream()
                        .map(rostered -> new NhlIdentityJoin.PlatformPlayer(
                                rostered.player().playerId(),
                                rostered.player().name(),
                                rostered.player().teamAbbrev(),
                                rostered.sweaterNumber()))
                        .toList())
                .nhlIdToPlatformId()
                .forEach((nhlId, platformId) -> nhlIds.put(String.valueOf(platformId), nhlId));
        Map<Integer, RestOfSeasonSkaterResponse> skaters = byNhlId(
                projectionServiceClient.restOfSeasonSkaters(season), RestOfSeasonSkaterResponse::getNhlId);
        Map<Integer, RestOfSeasonGoalieResponse> goalies = byNhlId(
                projectionServiceClient.restOfSeasonGoalies(season), RestOfSeasonGoalieResponse::getNhlId);

        List<ScoutRosterPlayer> players = new ArrayList<>();
        for (LeagueOwnTeam.Rostered rostered : team.players()) {
            PlannerRosterPlayer player = rostered.player();
            Long nhlId = nhlIds.get(player.playerId());
            ScoutLine line = null;
            if (nhlId != null && "goalie".equals(player.type())) {
                RestOfSeasonGoalieResponse row = goalies.get(nhlId.intValue());
                line = row == null ? null : goalieLine(ProjectionSeedService.seasonShape(row));
            } else if (nhlId != null) {
                RestOfSeasonSkaterResponse row = skaters.get(nhlId.intValue());
                line = row == null
                        ? null
                        : skaterLine(ProjectionSeedService.seasonShape(row), playsDefence(player.positions()));
            }
            players.add(new ScoutRosterPlayer(
                    player.playerId(),
                    player.name(),
                    player.teamAbbrev(),
                    player.type(),
                    player.positions(),
                    player.slot(),
                    player.injuryStatus(),
                    LeagueOwnTeam.parked(player.slot()),
                    player.out(),
                    line));
        }
        return new ScoutMyTeamResponse(true, team.name(), List.copyOf(players));
    }

    private static boolean playsDefence(List<String> positions) {
        return positions.stream()
                .anyMatch(position -> PlayerFieldMapping.fantasyPosition(position) == SkaterPosition.D);
    }

    private static <T> Map<Integer, T> byNhlId(List<T> rows, Function<T, Integer> nhlId) {
        Map<Integer, T> byId = new HashMap<>();
        if (rows != null) {
            for (T row : rows) {
                if (nhlId.apply(row) != null) {
                    byId.put(nhlId.apply(row), row);
                }
            }
        }
        return byId;
    }

    private static ScoutPlayerResponse row(
            Available player, String type, ScoutLine restOfSeason, ScoutLine preseason) {
        return new ScoutPlayerResponse(
                player.playerId(),
                player.name(),
                player.teamAbbrev(),
                type,
                player.positions(),
                player.availability(),
                restOfSeason,
                preseason);
    }

    /** A skater's line in the vocabulary the planner's free agents use, over its own games. */
    static ScoutLine skaterLine(SkaterProjectionResponse line, boolean playsDefence) {
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
        ModelStatMapping.putTotal(stats, "toi", line.getToiPerGameSeconds(), line.getGamesPlayed());
        putSum(stats, "stpg", line.getPpGoals(), line.getShGoals());
        putSum(stats, "stpa", line.getPpAssists(), line.getShAssists());
        putSum(stats, "stp", line.getPpPoints(), line.getShPoints());
        if (playsDefence) {
            put(stats, "defPoints", line.getPoints());
        }
        return new ScoutLine(number(line.getGamesPlayed()), stats);
    }

    /** A goalie's line over his starts, the games a goalie is picked up for. */
    static ScoutLine goalieLine(GoalieProjectionResponse line) {
        Map<String, Double> stats = new LinkedHashMap<>();
        put(stats, "gs", line.getGamesStarted());
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
        return new ScoutLine(number(line.getGamesStarted()), stats);
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
