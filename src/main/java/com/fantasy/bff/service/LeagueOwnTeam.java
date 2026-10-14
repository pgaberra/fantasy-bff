package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.PlannerRosterPlayer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The user's own team in a Yahoo or ESPN league, today, and every other team in it. The streamer
 * planner reads the user's to find the nights it has room, the FA scout to find who could make
 * room, and Player Scout reads them all to say who holds whom.
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

    /**
     * One team in the league as its platform lists it, the user's own or another manager's, with
     * the same per-player detail {@link Team} carries.
     */
    public record LeagueTeam(String name, boolean mine, List<Rostered> players) {}

    public Team read(String userId, PlayerIdSpace platform, String leagueId) {
        return read(userId, platform, leagueId, null);
    }

    /**
     * The user's team as set for {@code date}: the players it holds and the slot each sits in that
     * day, which for a day ahead is the lineup the manager has set for it. Null is today, as
     * {@link #read(String, PlayerIdSpace, String)}.
     */
    public Team read(String userId, PlayerIdSpace platform, String leagueId, LocalDate date) {
        return readAll(userId, platform, leagueId, date).stream()
                .filter(LeagueTeam::mine)
                .findFirst()
                .map(mine -> new Team(true, mine.name(), mine.players()))
                .orElseGet(LeagueOwnTeam::notFound);
    }

    /**
     * Every team in the league today, the user's own flagged: both platforms send every roster in
     * one answer, so a page that weighs the whole league reads it once.
     */
    public List<LeagueTeam> readAll(String userId, PlayerIdSpace platform, String leagueId) {
        return readAll(userId, platform, leagueId, null);
    }

    private List<LeagueTeam> readAll(String userId, PlayerIdSpace platform, String leagueId, LocalDate date) {
        return platform == PlayerIdSpace.YAHOO ? yahoo(userId, leagueId, date) : espn(userId, leagueId, date);
    }

    private List<LeagueTeam> yahoo(String userId, String leagueKey, LocalDate date) {
        var rosters = date == null
                ? yahooServiceClient.rosters(userId, leagueKey)
                : yahooServiceClient.rosters(userId, leagueKey, date);
        if (rosters == null) {
            return List.of();
        }
        return rosters.getTeams().stream()
                .map(team -> new LeagueTeam(
                        team.getName(),
                        Boolean.TRUE.equals(team.getMine()),
                        team.getPlayers().stream()
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
                                .toList()))
                .toList();
    }

    private List<LeagueTeam> espn(String userId, String leagueId, LocalDate date) {
        var rosters = date == null
                ? espnServiceClient.rosters(userId, leagueId)
                : espnServiceClient.rosters(userId, leagueId, date);
        if (rosters == null) {
            return List.of();
        }
        return rosters.getTeams().stream()
                .map(team -> new LeagueTeam(
                        team.getName(),
                        Boolean.TRUE.equals(team.getMine()),
                        team.getPlayers().stream()
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
                                .toList()))
                .toList();
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
