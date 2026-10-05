package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.PlannerRosterPlayer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The user's own team in a Yahoo or ESPN league, today. The streamer planner reads it to find the
 * nights it has room, and the FA scout to find who could make room.
 *
 * <p>Read live from the platform, never from the player pool: the roster changes with every pickup,
 * and the platform's club and positions for a player are today's, where the pool's can lag.
 */
@Component
public class LeagueOwnTeam {

    /** Slots that hold a player out of the lineup, on either platform. */
    static final Set<String> OUT_SLOTS = Set.of("IR", "IR+", "IR-LT", "IR-NR", "NA");
    /** Injury statuses that keep a player out, on either platform; day-to-day is not one. */
    private static final Set<String> OUT_STATUSES = Set.of(
            "O", "IR", "IR-LT", "IR-NR", "NA", "SUSP", "OUT", "INJURY_RESERVE", "SUSPENSION");
    /** The real positions; roster-only slots (Util, F, BN, IR) are not eligibility. */
    private static final Set<String> POSITIONS = Set.of("C", "LW", "RW", "D", "G");

    private final YahooServiceClient yahooServiceClient;
    private final EspnServiceClient espnServiceClient;

    public LeagueOwnTeam(YahooServiceClient yahooServiceClient, EspnServiceClient espnServiceClient) {
        this.yahooServiceClient = yahooServiceClient;
        this.espnServiceClient = espnServiceClient;
    }

    /**
     * The user's team, or one not found: a league they only follow, or an ESPN league read without
     * their cookies.
     */
    public record Team(boolean found, String name, List<Rostered> players) {}

    /**
     * A player on it, with the sweater number the platform has for him, for the join on identity,
     * and the injured-reserve slots he may be moved into ({@link InjuredReserve}).
     */
    public record Rostered(PlannerRosterPlayer player, Integer sweaterNumber, List<String> reserveEligible) {}

    public Team read(String userId, PlayerIdSpace platform, String leagueId) {
        return platform == PlayerIdSpace.YAHOO ? yahoo(userId, leagueId) : espn(userId, leagueId);
    }

    private Team yahoo(String userId, String leagueKey) {
        var rosters = yahooServiceClient.rosters(userId, leagueKey);
        var mine = rosters == null ? null : rosters.getTeams().stream()
                .filter(team -> Boolean.TRUE.equals(team.getMine()))
                .findFirst()
                .orElse(null);
        if (mine == null) {
            return notFound();
        }
        List<Rostered> players = mine.getPlayers().stream()
                .map(player -> new Rostered(
                        player(
                                String.valueOf(player.getPlayerId()),
                                player.getFullName(),
                                player.getTeamAbbrev(),
                                Boolean.TRUE.equals(player.getGoalie()),
                                player.getEligiblePositions(),
                                player.getSelectedPosition(),
                                player.getStatus()),
                        player.getUniformNumber(),
                        InjuredReserve.yahooEligible(player.getEligiblePositions())))
                .toList();
        return new Team(true, mine.getName(), players);
    }

    private Team espn(String userId, String leagueId) {
        var rosters = espnServiceClient.rosters(userId, leagueId);
        var mine = rosters == null ? null : rosters.getTeams().stream()
                .filter(team -> Boolean.TRUE.equals(team.getMine()))
                .findFirst()
                .orElse(null);
        if (mine == null) {
            return notFound();
        }
        List<Rostered> players = mine.getPlayers().stream()
                .map(player -> new Rostered(
                        player(
                                String.valueOf(player.getEspnId()),
                                player.getFullName(),
                                player.getTeamAbbrev(),
                                Boolean.TRUE.equals(player.getGoalie()),
                                player.getEligiblePositions(),
                                player.getLineupSlot(),
                                player.getInjuryStatus()),
                        player.getUniformNumber(),
                        InjuredReserve.espnEligible(player.getInjuryStatus())))
                .toList();
        return new Team(true, mine.getName(), players);
    }

    private static Team notFound() {
        return new Team(false, null, List.of());
    }

    private static PlannerRosterPlayer player(
            String playerId,
            String name,
            String teamAbbrev,
            boolean goalie,
            List<String> eligible,
            String slot,
            String injuryStatus) {
        List<String> positions = eligible == null
                ? List.of()
                : eligible.stream().filter(POSITIONS::contains).toList();
        return new PlannerRosterPlayer(
                playerId,
                name == null || name.isBlank() ? playerId : name,
                teamAbbrev,
                goalie ? "goalie" : "skater",
                positions,
                slot,
                injuryStatus,
                out(slot, injuryStatus));
    }

    static boolean out(String slot, String injuryStatus) {
        return parked(slot)
                || (injuryStatus != null && OUT_STATUSES.contains(injuryStatus.toUpperCase(Locale.ROOT)));
    }

    /** Whether the slot is injured reserve or not-active: one that takes no roster spot. */
    static boolean parked(String slot) {
        return slot != null && OUT_SLOTS.contains(slot.toUpperCase(Locale.ROOT));
    }
}
