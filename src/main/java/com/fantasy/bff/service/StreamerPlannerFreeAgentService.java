package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.FreeAgentListResponse;
import com.fantasy.bff.dto.response.FreeAgentResponse;
import com.fantasy.bff.dto.response.PlayerAvailability;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.generated.espn.model.AvailablePlayer;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.RangeGoalieResponse;
import com.fantasy.bff.generated.projection.model.RangeProjectionsResponse;
import com.fantasy.bff.generated.projection.model.RangeSkaterResponse;
import com.fantasy.bff.generated.yahoo.model.YahooAvailablePlayerResponse;
import com.fantasy.bff.service.mapping.ModelStatMapping;
import com.fantasy.bff.service.mapping.PlayerFieldMapping;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The streamer planner's second half: of the players a league has available, which ones the model
 * expects the most from over the chosen week.
 *
 * <p>The two sides are keyed differently — the platform numbers its players, the model numbers
 * NHL ids — and they are joined on <b>identity</b> here rather than through the player pool's id
 * space. That keeps the answer right whichever platform the pool is served from: a Yahoo league's
 * free agents carry Yahoo ids, which an ESPN-numbered pool could not resolve at all.
 *
 * <p>Nothing here scores the players. The stats go out in the projection's own vocabulary and the
 * client weighs them by the league's scoring settings, as it does everywhere else — a ranking
 * computed here could not be re-weighted, and every league weighs a shot block differently.
 */
@Service
public class StreamerPlannerFreeAgentService {

    private static final Logger log = LoggerFactory.getLogger(StreamerPlannerFreeAgentService.class);

    /** Available players asked of the platform. Deeper than any league's wire. */
    private static final int PLATFORM_LIMIT = 300;
    /** Projections asked of the model: the whole projected league, so no available player is missed. */
    private static final int PROJECTION_LIMIT = 2000;

    private final ProjectionServiceClient projectionServiceClient;
    private final YahooServiceClient yahooServiceClient;
    private final EspnServiceClient espnServiceClient;
    private final PlayerSplitContextProvider contextProvider;
    private final PlayerIdResolver resolver;
    private final StreamerPlannerAvailability availability;

    public StreamerPlannerFreeAgentService(
            ProjectionServiceClient projectionServiceClient,
            YahooServiceClient yahooServiceClient,
            EspnServiceClient espnServiceClient,
            PlayerSplitContextProvider contextProvider,
            PlayerIdResolver resolver,
            StreamerPlannerAvailability availability) {
        this.projectionServiceClient = projectionServiceClient;
        this.yahooServiceClient = yahooServiceClient;
        this.espnServiceClient = espnServiceClient;
        this.contextProvider = contextProvider;
        this.resolver = resolver;
        this.availability = availability;
    }

    /** One available player as both sides need him: the platform's id, his identity, his status. */
    private record Available(
            String playerId,
            String name,
            String teamAbbrev,
            List<String> positions,
            boolean goalie,
            Integer sweaterNumber,
            PlayerAvailability availability) {

        boolean playsDefence() {
            return positions != null
                    && positions.stream()
                            .anyMatch(position ->
                                    PlayerFieldMapping.fantasyPosition(position) == SkaterPosition.D);
        }
    }

    public FreeAgentListResponse freeAgents(
            String userId, PlayerIdSpace platform, String leagueId, LocalDate start, LocalDate end) {
        availability.require();
        StreamerPlannerService.requireStretch(start, end);
        List<Available> available = platform == PlayerIdSpace.YAHOO
                ? yahooAvailable(userId, leagueId)
                : espnAvailable(userId, leagueId);
        RangeProjectionsResponse projections =
                projectionServiceClient.rangeProjections(start, end, PROJECTION_LIMIT);

        Map<Long, PlayerResponse> identities = contextProvider.context().identities();
        Map<String, Available> byPlayerId = new LinkedHashMap<>();
        List<PlayerIdResolver.Candidate> platformSide = new ArrayList<>();
        for (Available player : available) {
            byPlayerId.put(player.playerId(), player);
            // The resolver works in numeric platform ids; a Yahoo id is a numeric string, so the
            // join key goes back to the platform's own form on the way out.
            Long numeric = numericId(player.playerId());
            if (numeric != null) {
                platformSide.add(new PlayerIdResolver.Candidate(
                        numeric, player.name(), player.teamAbbrev(), player.sweaterNumber()));
            }
        }

        List<PlayerIdResolver.Candidate> nhlSide = new ArrayList<>();
        for (PlayerResponse identity : identities.values()) {
            nhlSide.add(new PlayerIdResolver.Candidate(
                    identity.getNhlId(),
                    identity.getFullName(),
                    PlayerIdResolver.platformTeam(identity.getCurrentTeam()),
                    identity.getSweaterNumber()));
        }
        PlayerIdMapping mapping = resolver.resolve(nhlSide, platformSide, Map.of());

        List<FreeAgentResponse> rows = new ArrayList<>();
        for (RangeSkaterResponse skater : projections.getSkaters()) {
            Available player = matched(mapping, byPlayerId, skater.getNhlId());
            if (player != null && !player.goalie()) {
                rows.add(row(player, "skater", skater.getClubGames(), skater.getExpectedGames(),
                        skaterStats(skater, player.playsDefence())));
            }
        }
        for (RangeGoalieResponse goalie : projections.getGoalies()) {
            Available player = matched(mapping, byPlayerId, goalie.getNhlId());
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
                List.copyOf(rows));
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

    private static Available matched(
            PlayerIdMapping mapping, Map<String, Available> byPlayerId, Integer nhlId) {
        if (nhlId == null) {
            return null;
        }
        Integer platformId = mapping.nhlIdToPlatformId().get(nhlId.longValue());
        return platformId == null ? null : byPlayerId.get(String.valueOf(platformId));
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

    private List<Available> yahooAvailable(String userId, String leagueKey) {
        List<Available> available = new ArrayList<>();
        for (YahooAvailablePlayerResponse player :
                yahooServiceClient.leagueFreeAgents(userId, leagueKey, PLATFORM_LIMIT)) {
            available.add(new Available(
                    player.getYahooId(),
                    player.getFullName(),
                    player.getTeamAbbrev(),
                    player.getEligiblePositions(),
                    Boolean.TRUE.equals(player.getGoalie()),
                    player.getUniformNumber(),
                    availability(player.getAvailability().getValue())));
        }
        return available;
    }

    private List<Available> espnAvailable(String userId, String leagueId) {
        List<Available> available = new ArrayList<>();
        for (AvailablePlayer player :
                espnServiceClient.leagueFreeAgents(userId, leagueId, PLATFORM_LIMIT)) {
            available.add(new Available(
                    String.valueOf(player.getEspnId()),
                    player.getFullName(),
                    player.getTeamAbbrev(),
                    player.getEligiblePositions(),
                    Boolean.TRUE.equals(player.getGoalie()),
                    player.getUniformNumber(),
                    availability(player.getAvailability().getValue())));
        }
        return available;
    }

    /** The platforms' own wording, which their specs make required, mapped to the BFF's one name. */
    private static PlayerAvailability availability(String platformValue) {
        return switch (platformValue) {
            case "FREE_AGENT" -> PlayerAvailability.FREE_AGENT;
            case "WAIVERS" -> PlayerAvailability.WAIVERS;
            default -> PlayerAvailability.UNKNOWN;
        };
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

    private static Long numericId(String playerId) {
        if (playerId == null) {
            return null;
        }
        try {
            return Long.valueOf(playerId);
        } catch (NumberFormatException notNumeric) {
            return null;
        }
    }
}
