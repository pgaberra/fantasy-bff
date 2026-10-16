package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.PlayerAvailability;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.generated.espn.model.AvailablePlayer;
import com.fantasy.bff.generated.yahoo.model.YahooAvailablePlayerResponse;
import com.fantasy.bff.service.LeagueOwnTeam.LeagueTeam;
import com.fantasy.bff.service.mapping.PlayerFieldMapping;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The players a Yahoo or ESPN league has available, joined to the model's NHL ids on
 * <b>identity</b> ({@link NhlIdentityJoin}). The streamer planner and Player Scout both read a
 * league's wire this way.
 *
 * <p>The wire is the platform's whole player pool less every player the league rosters: a player
 * no team holds is available, however far down the platform's own ranking he sits. Asking the
 * platform for its available list instead means asking for a ranked page, and any cap on it drops
 * whoever ranks below it, so a player a rival had just cut could not be found at all. The
 * rosters are read whole in one call, and the pool is the one the app already keeps.
 *
 * <p>The platform still says who is on waivers: that list is short, so it is read whole, and
 * everyone else available is a free agent.
 */
@Component
public class LeagueAvailablePlayers {

    /**
     * Players on waivers asked of the platform, the most either service serves. A league's
     * waivers hold the players dropped in the last day or two, a few dozen at most.
     */
    static final int WAIVERS_LIMIT = 300;

    private final YahooServiceClient yahooServiceClient;
    private final EspnServiceClient espnServiceClient;
    private final PlayerServiceClient playerServiceClient;
    private final LeagueOwnTeam leagueTeams;
    private final NhlIdentityJoin identityJoin;
    private final int espnSeason;

    public LeagueAvailablePlayers(
            YahooServiceClient yahooServiceClient,
            EspnServiceClient espnServiceClient,
            PlayerServiceClient playerServiceClient,
            LeagueOwnTeam leagueTeams,
            NhlIdentityJoin identityJoin,
            @Value("${services.espn-fantasy.stats-season}") int espnSeason) {
        this.yahooServiceClient = yahooServiceClient;
        this.espnServiceClient = espnServiceClient;
        this.playerServiceClient = playerServiceClient;
        this.leagueTeams = leagueTeams;
        this.identityJoin = identityJoin;
        this.espnSeason = espnSeason;
    }

    /** One available player as both sides need him: the platform's id, his identity, his status. */
    public record Available(
            String playerId,
            String name,
            String teamAbbrev,
            List<String> positions,
            boolean goalie,
            Integer sweaterNumber,
            PlayerAvailability availability,
            Double percentRostered) {

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
     * @param players every available player, each once
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

    /** The league's available players, its rosters read here. */
    public Wire read(String userId, PlayerIdSpace platform, String leagueId) {
        return read(userId, platform, leagueId, leagueTeams.readAll(userId, platform, leagueId));
    }

    /**
     * The league's available players, given its rosters as {@link LeagueOwnTeam#readAll} reads
     * them, for a caller that has read them already.
     *
     * @throws IllegalStateException when the league came back with no teams: nobody could then be
     *     told rostered from available, and listing the whole pool as free would be a lie
     */
    public Wire read(String userId, PlayerIdSpace platform, String leagueId, List<LeagueTeam> teams) {
        if (teams.isEmpty()) {
            throw new IllegalStateException("The league's rosters came back with no teams");
        }
        Set<String> rostered = new HashSet<>();
        teams.forEach(team -> team.players().forEach(player -> rostered.add(player.player().playerId())));
        List<Available> pool = platform == PlayerIdSpace.YAHOO ? yahooPool() : espnPool();
        List<Available> waivers = platform == PlayerIdSpace.YAHOO
                ? yahooWaivers(userId, leagueId)
                : espnWaivers(userId, leagueId);
        List<Available> available = unrostered(pool, waivers, rostered);
        // Joined as the whole pool, rostered players included, and only then narrowed to the
        // wire: with a rostered man taken out first, the resolver's surname-and-initial fallback
        // could hand his NHL id to the one namesake left, and list him as available.
        List<Available> everyone = new ArrayList<>(pool);
        Set<String> pooled = new HashSet<>();
        pool.forEach(player -> pooled.add(player.playerId()));
        available.stream().filter(player -> !pooled.contains(player.playerId())).forEach(everyone::add);
        return wire(available, join(everyone));
    }

    /**
     * The whole Yahoo pool read as if it were one league's wire: every player Yahoo rosters in at
     * most {@code maxPercentRostered} of its leagues. The admins' demo stands this in for a league
     * it does not have. A player with no percentage is left out, since unknown is not low, and
     * nobody is a free agent or on waivers without a league, so every one is UNKNOWN.
     */
    public Wire readPool(double maxPercentRostered) {
        List<Available> available = new ArrayList<>();
        for (PlayerServiceClient.RosteredPlayer player : playerServiceClient.getRosteredPlayers()) {
            Double percent = player.percentRostered();
            if (percent != null && percent <= maxPercentRostered) {
                available.add(new Available(
                        player.yahooId(),
                        player.name(),
                        player.teamAbbrev(),
                        player.eligiblePositions(),
                        player.goalie(),
                        player.sweaterNumber(),
                        PlayerAvailability.UNKNOWN,
                        percent));
            }
        }
        return wire(available);
    }

    /**
     * Every pool player no team holds, a free agent unless the platform has him on waivers. A
     * player on waivers carries the platform's own row, which is today's club and positions, and
     * one the pool does not have yet (called up since its last sync) is listed all the same.
     */
    static List<Available> unrostered(List<Available> pool, List<Available> waivers, Set<String> rostered) {
        Map<String, Available> onWaivers = new LinkedHashMap<>();
        for (Available player : waivers) {
            // Asked for waivers alone, a row is on waivers unless it says it is a free agent: one
            // Yahoo sends without its ownership comes back UNKNOWN and is still on waivers, and an
            // older service that ignored the filter and sent free agents too marks them as such.
            if (player.availability() != PlayerAvailability.FREE_AGENT && !rostered.contains(player.playerId())) {
                onWaivers.put(player.playerId(), withAvailability(player, PlayerAvailability.WAIVERS));
            }
        }
        List<Available> available = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Available player : pool) {
            if (!rostered.contains(player.playerId()) && seen.add(player.playerId())) {
                Available waiver = onWaivers.remove(player.playerId());
                available.add(waiver != null ? waiver : withAvailability(player, PlayerAvailability.FREE_AGENT));
            }
        }
        available.addAll(onWaivers.values());
        return available;
    }

    private static Available withAvailability(Available player, PlayerAvailability availability) {
        return new Available(
                player.playerId(),
                player.name(),
                player.teamAbbrev(),
                player.positions(),
                player.goalie(),
                player.sweaterNumber(),
                availability,
                null);
    }

    private Wire wire(List<Available> available) {
        return wire(available, join(available));
    }

    /**
     * The wire with a join that may cover more players than are on it: {@link Wire#matched} only
     * answers for an id on the wire, so a rostered player the join resolved is still not available.
     */
    private static Wire wire(List<Available> available, PlayerIdMapping mapping) {
        Map<String, Available> byPlayerId = new LinkedHashMap<>();
        available.forEach(player -> byPlayerId.put(player.playerId(), player));
        return new Wire(List.copyOf(available), Collections.unmodifiableMap(byPlayerId), mapping);
    }

    private PlayerIdMapping join(List<Available> available) {
        return identityJoin.join(available.stream()
                .map(player -> new NhlIdentityJoin.PlatformPlayer(
                        player.playerId(), player.name(), player.teamAbbrev(), player.sweaterNumber()))
                .toList());
    }

    /** The Yahoo pool as yahoo-service keeps it; its availability is filled in by {@link #unrostered}. */
    private List<Available> yahooPool() {
        return playerServiceClient.getRosteredPlayers().stream()
                .map(player -> new Available(
                        player.yahooId(),
                        player.name(),
                        player.teamAbbrev(),
                        player.eligiblePositions(),
                        player.goalie(),
                        player.sweaterNumber(),
                        PlayerAvailability.UNKNOWN,
                        null))
                .toList();
    }

    /** The ESPN pool as espn-service keeps it, skaters then goalies. */
    private List<Available> espnPool() {
        List<Available> pool = new ArrayList<>();
        espnServiceClient.skaters(espnSeason).forEach(skater -> pool.add(new Available(
                String.valueOf(skater.getId()),
                fullName(skater.getFirstName(), skater.getLastName()),
                skater.getTeamAbbrev(),
                skater.getEligiblePositions(),
                false,
                skater.getSweaterNumber(),
                PlayerAvailability.UNKNOWN,
                null)));
        espnServiceClient.goalies(espnSeason).forEach(goalie -> pool.add(new Available(
                String.valueOf(goalie.getId()),
                fullName(goalie.getFirstName(), goalie.getLastName()),
                goalie.getTeamAbbrev(),
                goalie.getEligiblePositions(),
                true,
                goalie.getSweaterNumber(),
                PlayerAvailability.UNKNOWN,
                null)));
        return pool;
    }

    private static String fullName(String firstName, String lastName) {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    private List<Available> yahooWaivers(String userId, String leagueKey) {
        List<Available> waivers = new ArrayList<>();
        for (YahooAvailablePlayerResponse player : yahooServiceClient.leagueWaivers(userId, leagueKey, WAIVERS_LIMIT)) {
            waivers.add(new Available(
                    player.getYahooId(),
                    player.getFullName(),
                    player.getTeamAbbrev(),
                    player.getEligiblePositions(),
                    Boolean.TRUE.equals(player.getGoalie()),
                    player.getUniformNumber(),
                    availability(player.getAvailability().getValue()),
                    null));
        }
        return waivers;
    }

    private List<Available> espnWaivers(String userId, String leagueId) {
        List<Available> waivers = new ArrayList<>();
        for (AvailablePlayer player : espnServiceClient.leagueWaivers(userId, leagueId, WAIVERS_LIMIT)) {
            waivers.add(new Available(
                    String.valueOf(player.getEspnId()),
                    player.getFullName(),
                    player.getTeamAbbrev(),
                    player.getEligiblePositions(),
                    Boolean.TRUE.equals(player.getGoalie()),
                    player.getUniformNumber(),
                    availability(player.getAvailability().getValue()),
                    null));
        }
        return waivers;
    }

    /** The platforms' own wording, which their specs make required, mapped to the BFF's one name. */
    private static PlayerAvailability availability(String platformValue) {
        return switch (platformValue) {
            case "FREE_AGENT" -> PlayerAvailability.FREE_AGENT;
            case "WAIVERS" -> PlayerAvailability.WAIVERS;
            default -> PlayerAvailability.UNKNOWN;
        };
    }
}
