package com.fantasy.bff.service;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.ClubGames;
import com.fantasy.bff.dto.response.LineupListing;
import com.fantasy.bff.dto.response.PlannerRosterPlayer;
import com.fantasy.bff.dto.response.PlayerScoutListResponse;
import com.fantasy.bff.dto.response.PlayerScoutRow;
import com.fantasy.bff.dto.response.PlayerScoutTeamResponse;
import com.fantasy.bff.dto.response.RoleWindow;
import com.fantasy.bff.dto.response.ScoutLine;
import com.fantasy.bff.dto.response.ScoutMyTeamResponse;
import com.fantasy.bff.dto.response.ScoutOwner;
import com.fantasy.bff.dto.response.ScoutSince;
import com.fantasy.bff.dto.response.ScoutUsage;
import com.fantasy.bff.generated.projection.model.GoalieProjectionResponse;
import com.fantasy.bff.generated.projection.model.LineupListingResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonGoalieResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonSkaterResponse;
import com.fantasy.bff.generated.projection.model.RestOfSeasonSnapshotsResponse;
import com.fantasy.bff.generated.projection.model.RoleChangesResponse;
import com.fantasy.bff.generated.projection.model.RoleWindowResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.generated.projection.model.SkaterRoleChangeResponse;
import com.fantasy.bff.service.LeagueAvailablePlayers.Available;
import com.fantasy.bff.service.LeagueOwnTeam.LeagueTeam;
import com.fantasy.bff.service.LeagueOwnTeam.Rostered;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import com.fantasy.bff.service.scoring.LeagueSchedule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Player Scout: how the model's view of every player in a league has moved since a chosen day,
 * and what the ice says about why. The FA scout and Role Changes in one page.
 *
 * <p>Each player goes out with two of the model's lines, both the served rest of the season
 * (lifted to the season line's scale, the season so far left out): today's, and the one as it
 * stood on the chosen day, re-spread over the games his club has left today
 * ({@link ScoutRescale}). The day is answered from projection-service's day-by-day history, the
 * newest day kept on or before it; before the history began, or asked for no day, the model's
 * preseason line answers instead, and {@link ScoutSince} says which. Beside the lines travels his
 * measured ice since that same day, so one date asks both questions.
 *
 * <p>Nothing is scored here, as in the planner: the client ranks both lines by the league's own
 * settings, and the distance between them is what says a player has risen. Every player the
 * league holds is listed, available or rostered, each with who holds him; the client filters.
 */
@Service
public class PlayerScoutService {

    /** The model version projection-service freezes the eve of the season under. */
    static final String PRESEASON = ScoutLineService.PRESEASON;

    /** Games in the schedule when no club's count is known; the league has played 82 since 1995. */
    static final int SEASON_GAMES_FALLBACK = 82;

    private final ProjectionServiceClient projectionServiceClient;
    private final LeagueAvailablePlayers leagueAvailablePlayers;
    private final LeagueOwnTeam leagueTeams;
    private final NhlIdentityJoin identityJoin;
    private final ScoutLineService scoutLineService;
    private final SeasonScheduleService scheduleService;
    private final PlayerScoutAvailability availability;
    private final int season;

    public PlayerScoutService(
            ProjectionServiceClient projectionServiceClient,
            LeagueAvailablePlayers leagueAvailablePlayers,
            LeagueOwnTeam leagueTeams,
            NhlIdentityJoin identityJoin,
            ScoutLineService scoutLineService,
            SeasonScheduleService scheduleService,
            PlayerScoutAvailability availability,
            @Value("${services.projection.season}") int season) {
        this.projectionServiceClient = projectionServiceClient;
        this.leagueAvailablePlayers = leagueAvailablePlayers;
        this.leagueTeams = leagueTeams;
        this.identityJoin = identityJoin;
        this.scoutLineService = scoutLineService;
        this.scheduleService = scheduleService;
        this.availability = availability;
        this.season = season;
    }

    /**
     * Every player in the league the model projects, with its view of him now and on {@code since}
     * (null for the preseason line), and his ice since that day.
     */
    public PlayerScoutListResponse players(
            String userId, PlayerIdSpace platform, String leagueId, LocalDate since, Integer recentGames) {
        availability.require();
        List<RestOfSeasonSkaterResponse> skaters = projectionServiceClient.restOfSeasonSkaters(season);
        List<RestOfSeasonGoalieResponse> goalies = projectionServiceClient.restOfSeasonGoalies(season);
        if (skaters.isEmpty() && goalies.isEmpty()) {
            // Before the first game or after the last: no rest of the season, so the league is not
            // worth a call to the platform.
            return new PlayerScoutListResponse(
                    season, null, false, new ScoutSince(since, "NONE", null, null), recentGames, List.of());
        }

        Holders holders = holders(userId, platform, leagueId);
        Then then = then(since);
        Map<Integer, ScoutUsage> usage = usage(since, recentGames);

        List<PlayerScoutRow> rows = new ArrayList<>();
        for (RestOfSeasonSkaterResponse row : skaters) {
            Holder holder = holders.of(row.getNhlId());
            if (holder != null && !holder.goalie()) {
                boolean defence = holder.playsDefence();
                ScoutLine now = ScoutLineService.skaterLine(ProjectionSeedService.seasonShape(row), defence);
                rows.add(holder.row(
                        row.getNhlId(),
                        "skater",
                        row.getGamesRemaining(),
                        now,
                        then.skater(row, defence),
                        usage.get(row.getNhlId())));
            }
        }
        for (RestOfSeasonGoalieResponse row : goalies) {
            Holder holder = holders.of(row.getNhlId());
            if (holder != null && holder.goalie()) {
                ScoutLine now = ScoutLineService.goalieLine(ProjectionSeedService.seasonShape(row));
                rows.add(holder.row(
                        row.getNhlId(), "goalie", row.getGamesRemaining(), now, then.goalie(row), null));
            }
        }
        // A default order, not a ranking: the client scores them by the league's settings.
        rows.sort(Comparator.comparingDouble((PlayerScoutRow row) -> row.now().games())
                .reversed()
                .thenComparing(PlayerScoutRow::name));

        String modelVersion = !skaters.isEmpty()
                ? skaters.getFirst().getModelVersion()
                : goalies.getFirst().getModelVersion();
        return new PlayerScoutListResponse(
                season, modelVersion, true, then.since(), recentGames, List.copyOf(rows));
    }

    /**
     * The user's own team on the rest-of-season line the league's players carry, and every club's
     * remaining nights for the client to seat the roster over.
     */
    public PlayerScoutTeamResponse myTeam(String userId, PlayerIdSpace platform, String leagueId) {
        availability.require();
        ScoutMyTeamResponse team = scoutLineService.restOfSeasonTeam(userId, platform, leagueId);
        List<ClubGames> schedule = new ArrayList<>();
        scheduleService.schedule().window().forEach((club, dates) -> schedule.add(new ClubGames(club, dates)));
        schedule.sort(Comparator.comparing(ClubGames::team));
        return new PlayerScoutTeamResponse(team.found(), team.teamName(), team.players(), List.copyOf(schedule));
    }

    // --- Who holds whom ---------------------------------------------------------------------------

    /** A player as the league has him: available, or on one of its teams. */
    private interface Holder {
        boolean goalie();

        boolean playsDefence();

        PlayerScoutRow row(int nhlId, String type, int gamesRemaining, ScoutLine now, ScoutLine then, ScoutUsage usage);
    }

    private record AvailableHolder(Available player) implements Holder {
        @Override
        public boolean goalie() {
            return player.goalie();
        }

        @Override
        public boolean playsDefence() {
            return player.playsDefence();
        }

        @Override
        public PlayerScoutRow row(
                int nhlId, String type, int gamesRemaining, ScoutLine now, ScoutLine then, ScoutUsage usage) {
            return new PlayerScoutRow(
                    player.playerId(),
                    nhlId,
                    player.name(),
                    player.teamAbbrev(),
                    type,
                    player.positions(),
                    player.availability(),
                    null,
                    null,
                    null,
                    gamesRemaining,
                    now,
                    then,
                    usage);
        }
    }

    private record RosteredHolder(LeagueTeam team, PlannerRosterPlayer player) implements Holder {
        @Override
        public boolean goalie() {
            return "goalie".equals(player.type());
        }

        @Override
        public boolean playsDefence() {
            return player.positions().contains("D");
        }

        @Override
        public PlayerScoutRow row(
                int nhlId, String type, int gamesRemaining, ScoutLine now, ScoutLine then, ScoutUsage usage) {
            return new PlayerScoutRow(
                    player.playerId(),
                    nhlId,
                    player.name(),
                    player.teamAbbrev(),
                    type,
                    player.positions(),
                    null,
                    new ScoutOwner(team.name(), team.mine()),
                    player.slot(),
                    player.injuryStatus(),
                    gamesRemaining,
                    now,
                    then,
                    usage);
        }
    }

    private record Holders(LeagueAvailablePlayers.Wire wire, Map<Integer, Holder> rostered) {
        Holder of(Integer nhlId) {
            Available available = wire.matched(nhlId);
            if (available != null) {
                return new AvailableHolder(available);
            }
            return nhlId == null ? null : rostered.get(nhlId);
        }
    }

    /**
     * The league's wire and every team's roster, each joined to the model's ids on identity. The
     * rosters are joined as one list, since a player sits on one team at a time.
     */
    private Holders holders(String userId, PlayerIdSpace platform, String leagueId) {
        LeagueAvailablePlayers.Wire wire = leagueAvailablePlayers.read(userId, platform, leagueId);
        List<LeagueTeam> teams = leagueTeams.readAll(userId, platform, leagueId);
        Map<String, RosteredHolder> byPlatformId = new HashMap<>();
        List<NhlIdentityJoin.PlatformPlayer> players = new ArrayList<>();
        for (LeagueTeam team : teams) {
            for (Rostered rostered : team.players()) {
                PlannerRosterPlayer player = rostered.player();
                byPlatformId.put(player.playerId(), new RosteredHolder(team, player));
                players.add(new NhlIdentityJoin.PlatformPlayer(
                        player.playerId(), player.name(), player.teamAbbrev(), rostered.sweaterNumber()));
            }
        }
        Map<Integer, Holder> rostered = new HashMap<>();
        if (!players.isEmpty()) {
            identityJoin.join(players).nhlIdToPlatformId().forEach((nhlId, platformId) -> {
                RosteredHolder holder = byPlatformId.get(String.valueOf(platformId));
                if (holder != null) {
                    rostered.put(nhlId.intValue(), holder);
                }
            });
        }
        return new Holders(wire, rostered);
    }

    // --- The earlier line -------------------------------------------------------------------------

    /** The lines of the chosen day, by NHL id, with the schedule each was spread over. */
    private final class Then {
        private final ScoutSince since;
        private final Map<Integer, RestOfSeasonSkaterResponse> keptSkaters;
        private final Map<Integer, RestOfSeasonGoalieResponse> keptGoalies;
        private final Map<Integer, SkaterProjectionResponse> preseasonSkaters;
        private final Map<Integer, GoalieProjectionResponse> preseasonGoalies;
        private LeagueSchedule schedule;

        private Then(
                ScoutSince since,
                Map<Integer, RestOfSeasonSkaterResponse> keptSkaters,
                Map<Integer, RestOfSeasonGoalieResponse> keptGoalies,
                Map<Integer, SkaterProjectionResponse> preseasonSkaters,
                Map<Integer, GoalieProjectionResponse> preseasonGoalies) {
            this.since = since;
            this.keptSkaters = keptSkaters;
            this.keptGoalies = keptGoalies;
            this.preseasonSkaters = preseasonSkaters;
            this.preseasonGoalies = preseasonGoalies;
        }

        ScoutSince since() {
            return since;
        }

        ScoutLine skater(RestOfSeasonSkaterResponse now, boolean defence) {
            RestOfSeasonSkaterResponse kept = keptSkaters.get(now.getNhlId());
            if (kept != null) {
                ScoutLine line = ScoutLineService.skaterLine(ProjectionSeedService.seasonShape(kept), defence);
                return ScoutRescale.toGamesLeft(line, kept.getGamesRemaining(), now.getGamesRemaining());
            }
            SkaterProjectionResponse preseason = preseasonSkaters.get(now.getNhlId());
            if (preseason != null) {
                ScoutLine line = ScoutLineService.skaterLine(preseason, defence);
                return ScoutRescale.toGamesLeft(line, seasonGames(now.getTeam()), now.getGamesRemaining());
            }
            return null;
        }

        ScoutLine goalie(RestOfSeasonGoalieResponse now) {
            RestOfSeasonGoalieResponse kept = keptGoalies.get(now.getNhlId());
            if (kept != null) {
                ScoutLine line = ScoutLineService.goalieLine(ProjectionSeedService.seasonShape(kept));
                return ScoutRescale.toGamesLeft(line, kept.getGamesRemaining(), now.getGamesRemaining());
            }
            GoalieProjectionResponse preseason = preseasonGoalies.get(now.getNhlId());
            if (preseason != null) {
                ScoutLine line = ScoutLineService.goalieLine(preseason);
                return ScoutRescale.toGamesLeft(line, seasonGames(now.getTeam()), now.getGamesRemaining());
            }
            return null;
        }

        /**
         * The games the club's whole season has, which the preseason line was spread over. Read
         * off the published schedule once, and only for a page that needs it.
         */
        private int seasonGames(String club) {
            if (schedule == null) {
                schedule = scheduleService.schedule();
            }
            Integer games = schedule.seasonGames().get(PlayerIdResolver.platformTeam(club));
            if (games != null) {
                return games;
            }
            return schedule.seasonGames().values().stream().max(Integer::compare).orElse(SEASON_GAMES_FALLBACK);
        }
    }

    /**
     * The chosen day's lines: the newest kept on or before {@code since}, else the preseason line,
     * which is the newest line before any day in the season.
     */
    private Then then(LocalDate since) {
        LocalDate earliest = earliestSnapshot();
        if (since != null) {
            List<RestOfSeasonSkaterResponse> kept = projectionServiceClient.restOfSeasonSkaters(season, since);
            List<RestOfSeasonGoalieResponse> keptGoalies = projectionServiceClient.restOfSeasonGoalies(season, since);
            if (!kept.isEmpty() || !keptGoalies.isEmpty()) {
                LocalDate resolved = !kept.isEmpty()
                        ? kept.getFirst().getGeneratedAt().toLocalDate()
                        : keptGoalies.getFirst().getGeneratedAt().toLocalDate();
                return new Then(
                        new ScoutSince(since, "SNAPSHOT", resolved, earliest),
                        byNhlId(kept, RestOfSeasonSkaterResponse::getNhlId),
                        byNhlId(keptGoalies, RestOfSeasonGoalieResponse::getNhlId),
                        Map.of(),
                        Map.of());
            }
        }
        Map<Integer, SkaterProjectionResponse> preseasonSkaters = byNhlId(
                projectionServiceClient.skaterProjections(season, PRESEASON), SkaterProjectionResponse::getNhlId);
        Map<Integer, GoalieProjectionResponse> preseasonGoalies = byNhlId(
                projectionServiceClient.goalieProjections(season, PRESEASON), GoalieProjectionResponse::getNhlId);
        boolean stored = !preseasonSkaters.isEmpty() || !preseasonGoalies.isEmpty();
        return new Then(
                new ScoutSince(since, stored ? "PRESEASON" : "NONE", null, earliest),
                Map.of(),
                Map.of(),
                preseasonSkaters,
                preseasonGoalies);
    }

    private LocalDate earliestSnapshot() {
        RestOfSeasonSnapshotsResponse snapshots = projectionServiceClient.restOfSeasonSnapshots(season);
        if (snapshots == null || snapshots.getDays().isEmpty()) {
            return null;
        }
        return snapshots.getDays().getFirst();
    }

    // --- The ice since ----------------------------------------------------------------------------

    /**
     * Each skater's measured usage: over his club's last {@code recentGames} when asked for, else
     * since the chosen day. Asked for the preseason line, the whole season so far is "since": a
     * day before any season's first game makes last season the baseline, which is what the
     * preseason comparison means on the ice.
     */
    private Map<Integer, ScoutUsage> usage(LocalDate since, Integer recentGames) {
        RoleChangesResponse measured;
        if (recentGames != null) {
            measured = projectionServiceClient.skaterRoleChanges(season, recentGames);
        } else {
            LocalDate from = since != null ? since : LocalDate.of(season, 9, 1);
            measured = projectionServiceClient.skaterRoleChangesSince(season, from);
        }
        Map<Integer, ScoutUsage> byNhlId = new HashMap<>();
        if (measured == null) {
            return byNhlId;
        }
        for (SkaterRoleChangeResponse skater : measured.getSkaters()) {
            var source = skater.getBaselineSource();
            byNhlId.put(skater.getNhlId(), new ScoutUsage(
                    window(skater.getRecent()),
                    window(skater.getBaseline()),
                    source == null ? null : source.name(),
                    listing(skater.getListedNow()),
                    listing(skater.getListedBefore())));
        }
        return byNhlId;
    }

    private static RoleWindow window(RoleWindowResponse window) {
        if (window == null) {
            return null;
        }
        return new RoleWindow(
                window.getGames(),
                window.getToiPerGame().doubleValue(),
                number(window.getPpToiPerGame()),
                number(window.getPpShare()),
                number(window.getShToiPerGame()));
    }

    private static LineupListing listing(LineupListingResponse listing) {
        if (listing == null) {
            return null;
        }
        return new LineupListing(
                listing.getSeenOn(),
                listing.getLine(),
                listing.getPowerPlayUnit(),
                Boolean.TRUE.equals(listing.getOutOfLineup()));
    }

    private static Double number(BigDecimal value) {
        return value == null ? null : value.doubleValue();
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
}
