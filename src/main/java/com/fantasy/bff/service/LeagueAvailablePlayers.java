package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.PlayerAvailability;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.generated.espn.model.AvailablePlayer;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.yahoo.model.YahooAvailablePlayerResponse;
import com.fantasy.bff.service.mapping.PlayerFieldMapping;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * The players a Yahoo or ESPN league has available, joined to the model's NHL ids on
 * <b>identity</b>. The streamer planner and the FA scout both read a league's wire this way.
 *
 * <p>The two sides are keyed differently: the platform numbers its players and the model numbers
 * NHL ids. They are joined here on identity rather than through the player pool's id space, which
 * keeps the answer right whichever platform the pool is served from. A Yahoo league's free agents
 * carry Yahoo ids, and an ESPN-numbered pool could not resolve them at all.
 */
@Component
public class LeagueAvailablePlayers {

    /**
     * Available players asked of the platform, one position at a time. A single mixed list ranks
     * every goalie behind hundreds of skaters, so a cap on it cut off the goalies first: at 300 a
     * Yahoo league came back with six. Fifty goalies is a league's whole goalie wire. 75 skaters
     * a position is three Yahoo pages, so the slowest position is three calls deep.
     * Goalies come first because they are fetched alone; see {@link #byPosition}.
     */
    private static final Map<String, Integer> POSITION_LIMITS = orderedLimits();

    private final YahooServiceClient yahooServiceClient;
    private final EspnServiceClient espnServiceClient;
    private final PlayerSplitContextProvider contextProvider;
    private final PlayerIdResolver resolver;

    public LeagueAvailablePlayers(
            YahooServiceClient yahooServiceClient,
            EspnServiceClient espnServiceClient,
            PlayerSplitContextProvider contextProvider,
            PlayerIdResolver resolver) {
        this.yahooServiceClient = yahooServiceClient;
        this.espnServiceClient = espnServiceClient;
        this.contextProvider = contextProvider;
        this.resolver = resolver;
    }

    /** One available player as both sides need him: the platform's id, his identity, his status. */
    public record Available(
            String playerId,
            String name,
            String teamAbbrev,
            List<String> positions,
            boolean goalie,
            Integer sweaterNumber,
            PlayerAvailability availability) {

        public boolean playsDefence() {
            return positions != null
                    && positions.stream()
                            .anyMatch(position ->
                                    PlayerFieldMapping.fantasyPosition(position) == SkaterPosition.D);
        }
    }

    /**
     * A league's available players with the join to the model's ids.
     *
     * @param players every available player, each once, in the platform's order
     * @param byPlayerId the same players, by the platform's id
     * @param mapping the NHL id each one resolved to, where one did
     */
    public record Wire(List<Available> players, Map<String, Available> byPlayerId, PlayerIdMapping mapping) {

        /** The available player the model's {@code nhlId} is, or null when he is not available. */
        public Available matched(Integer nhlId) {
            if (nhlId == null) {
                return null;
            }
            Integer platformId = mapping.nhlIdToPlatformId().get(nhlId.longValue());
            if (platformId == null) {
                return null;
            }
            return byPlayerId.get(String.valueOf(platformId));
        }
    }

    /** The league's available players, read from the platform and joined on identity. */
    public Wire read(String userId, PlayerIdSpace platform, String leagueId) {
        List<Available> available = platform == PlayerIdSpace.YAHOO
                ? yahooAvailable(userId, leagueId)
                : espnAvailable(userId, leagueId);
        Map<String, Available> byPlayerId = new LinkedHashMap<>();
        available.forEach(player -> byPlayerId.put(player.playerId(), player));
        return new Wire(available, Collections.unmodifiableMap(byPlayerId), join(available));
    }

    private PlayerIdMapping join(List<Available> available) {
        Map<Long, PlayerResponse> identities = contextProvider.context().identities();
        List<PlayerIdResolver.Candidate> platformSide = new ArrayList<>();
        for (Available player : available) {
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
        return resolver.resolve(nhlSide, platformSide, Map.of());
    }

    private static Map<String, Integer> orderedLimits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("G", 50);
        limits.put("C", 75);
        limits.put("LW", 75);
        limits.put("RW", 75);
        limits.put("D", 75);
        return Collections.unmodifiableMap(limits);
    }

    /**
     * Every position's available players, each player once: a player eligible at two positions
     * comes back under both. The first position is fetched alone and the rest together. Each
     * call checks the user's stored platform token, and fetching the first alone means only one
     * call can find it expired and refresh it.
     */
    private static <T> List<T> byPosition(
            BiFunction<String, Integer, List<T>> fetch, Function<T, String> playerId) {
        Iterator<Map.Entry<String, Integer>> positions = POSITION_LIMITS.entrySet().iterator();
        Map.Entry<String, Integer> lead = positions.next();
        Map<String, T> unique = new LinkedHashMap<>();
        addUnique(unique, fetch.apply(lead.getKey(), lead.getValue()), playerId);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<T>>> rest = new ArrayList<>();
            positions.forEachRemaining(position -> rest.add(
                    executor.submit(() -> fetch.apply(position.getKey(), position.getValue()))));
            for (Future<List<T>> players : rest) {
                addUnique(unique, await(players), playerId);
            }
        }
        return List.copyOf(unique.values());
    }

    private static <T> void addUnique(Map<String, T> unique, List<T> players, Function<T, String> playerId) {
        for (T player : players) {
            unique.putIfAbsent(playerId.apply(player), player);
        }
    }

    /** The call's own exception, so a refusal or a downstream failure maps as it would unthreaded. */
    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading available players", interrupted);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException("Reading available players failed", failed.getCause());
        }
    }

    private List<Available> yahooAvailable(String userId, String leagueKey) {
        List<Available> available = new ArrayList<>();
        for (YahooAvailablePlayerResponse player : byPosition(
                (position, limit) -> yahooServiceClient.leagueFreeAgents(userId, leagueKey, position, limit),
                YahooAvailablePlayerResponse::getYahooId)) {
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
        for (AvailablePlayer player : byPosition(
                (position, limit) -> espnServiceClient.leagueFreeAgents(userId, leagueId, position, limit),
                player -> String.valueOf(player.getEspnId()))) {
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
