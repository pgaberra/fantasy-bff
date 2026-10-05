package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.PlannerMyTeamResponse;
import com.fantasy.bff.dto.response.PlannerRosterPlayer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The streamer planner's own-team half: the user's team in the chosen league, so the client can
 * place its players in the league's lineup night by night and see which slots are left open.
 *
 * <p>Read live from the platform, never from the player pool: the roster changes with every pickup,
 * and the platform's club and positions for a player are today's, where the pool's can lag. Which
 * nights a player plays is his club's schedule, which the client already has; this only says who he
 * is, where he may start and whether he is out.
 */
@Service
public class StreamerPlannerMyTeamService {

    /** Slots that hold a player out of the lineup, on either platform. */
    private static final Set<String> OUT_SLOTS = Set.of("IR", "IR+", "IR-LT", "IR-NR", "NA");
    /** Injury statuses that keep a player out, on either platform; day-to-day is not one. */
    private static final Set<String> OUT_STATUSES = Set.of(
            "O", "IR", "IR-LT", "IR-NR", "NA", "SUSP", "OUT", "INJURY_RESERVE", "SUSPENSION");
    /** The real positions; roster-only slots (Util, F, BN, IR) are not eligibility. */
    private static final Set<String> POSITIONS = Set.of("C", "LW", "RW", "D", "G");

    private final StreamerPlannerAvailability availability;
    private final YahooServiceClient yahooServiceClient;
    private final EspnServiceClient espnServiceClient;

    public StreamerPlannerMyTeamService(
            StreamerPlannerAvailability availability,
            YahooServiceClient yahooServiceClient,
            EspnServiceClient espnServiceClient) {
        this.availability = availability;
        this.yahooServiceClient = yahooServiceClient;
        this.espnServiceClient = espnServiceClient;
    }

    public PlannerMyTeamResponse myTeam(String userId, PlayerIdSpace platform, String leagueId) {
        availability.requireMyTeam();
        return platform == PlayerIdSpace.YAHOO ? yahoo(userId, leagueId) : espn(userId, leagueId);
    }

    private PlannerMyTeamResponse yahoo(String userId, String leagueKey) {
        var rosters = yahooServiceClient.rosters(userId, leagueKey);
        var mine = rosters == null ? null : rosters.getTeams().stream()
                .filter(team -> Boolean.TRUE.equals(team.getMine()))
                .findFirst()
                .orElse(null);
        if (mine == null) {
            return notFound();
        }
        List<PlannerRosterPlayer> players = mine.getPlayers().stream()
                .map(player -> player(
                        String.valueOf(player.getPlayerId()),
                        player.getFullName(),
                        player.getTeamAbbrev(),
                        Boolean.TRUE.equals(player.getGoalie()),
                        player.getEligiblePositions(),
                        player.getSelectedPosition(),
                        player.getStatus()))
                .toList();
        return new PlannerMyTeamResponse(true, mine.getName(), players);
    }

    private PlannerMyTeamResponse espn(String userId, String leagueId) {
        var rosters = espnServiceClient.rosters(userId, leagueId);
        var mine = rosters == null ? null : rosters.getTeams().stream()
                .filter(team -> Boolean.TRUE.equals(team.getMine()))
                .findFirst()
                .orElse(null);
        if (mine == null) {
            return notFound();
        }
        List<PlannerRosterPlayer> players = mine.getPlayers().stream()
                .map(player -> player(
                        String.valueOf(player.getEspnId()),
                        player.getFullName(),
                        player.getTeamAbbrev(),
                        Boolean.TRUE.equals(player.getGoalie()),
                        player.getEligiblePositions(),
                        player.getLineupSlot(),
                        player.getInjuryStatus()))
                .toList();
        return new PlannerMyTeamResponse(true, mine.getName(), players);
    }

    private static PlannerMyTeamResponse notFound() {
        return new PlannerMyTeamResponse(false, null, List.of());
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
        return (slot != null && OUT_SLOTS.contains(slot.toUpperCase(Locale.ROOT)))
                || (injuryStatus != null && OUT_STATUSES.contains(injuryStatus.toUpperCase(Locale.ROOT)));
    }
}
