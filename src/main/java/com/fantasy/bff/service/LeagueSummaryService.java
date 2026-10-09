package com.fantasy.bff.service;

import com.fantasy.bff.dto.request.ProjectionKind;
import com.fantasy.bff.dto.response.DraftPick;
import com.fantasy.bff.dto.response.DraftSettings;
import com.fantasy.bff.dto.response.DraftState;
import com.fantasy.bff.dto.response.DraftTeam;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.LeagueDraftPick;
import com.fantasy.bff.dto.response.LeagueDraftResponse;
import com.fantasy.bff.dto.response.LeagueDraftStatus;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.dto.response.PlayerProjection;
import com.fantasy.bff.dto.response.PositionOverride;
import com.fantasy.bff.dto.response.ProjectionResponse;
import com.fantasy.bff.dto.response.ProjectionSettings;
import com.fantasy.bff.dto.response.RosterSlots;
import com.fantasy.bff.dto.response.ScoringBasis;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.dto.response.SummarySource;
import com.fantasy.bff.service.scoring.LeagueScoring;
import com.fantasy.bff.service.scoring.LeagueSummary;
import com.fantasy.bff.service.scoring.LeagueSummaryCalculator;
import com.fantasy.bff.service.scoring.ScoredPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Where a league stands: every team's current roster totalled against a projection, counting what
 * its best lineup would start night by night over the NHL schedule — the rest of the season once
 * it is under way, the whole season before it.
 *
 * <p>This is the cold path — a manager who plays in Yahoo and has never built a board here.
 * Three things are deliberately <b>not</b> taken from the caller: the rosters, the teams and the
 * scoring settings all come from the league itself. A summary computed over rosters the caller
 * chose would be a per-player readout of the projection dressed as an aggregate: ask for a league
 * of one-player teams and every "team total" is one player's value. Reading a real league is the
 * thing being offered, and it is also what keeps the offer from being a way around what the
 * model's lines cost.
 *
 * <p>A team is the players Yahoo has on its roster today, so a trade, a drop or a pickup since the
 * draft moves the totals. Until the draft is over, and in a league whose rosters Yahoo lists all
 * empty, a team is its picks instead: that is what a live draft's table follows, pick by pick.
 *
 * <p>An ESPN league is totalled the same way, from what its teams hold on ESPN today; a drafted
 * player is on his ESPN roster at once, so a draft in progress is its picks so far there too.
 *
 * <p>A draft made here is the other thing it totals: a mock draft, or one played against a
 * league it cannot read, whose picks exist nowhere else. There the teams, the picks and the scoring are the
 * draft's own, which the user chose, unlike a league's.
 *
 * <p>The totals and the players behind them are everyone's, whichever kind of league is being
 * ranked and whichever projection it is ranked on (Alexander's call, 2026-10-09; until then the
 * players were premium's against the model and last season).
 */
@Service
public class LeagueSummaryService {

    /**
     * What a goalie must be projected to play to qualify in a category league. The league does
     * not state it — it is the app's own default, the same number a board here starts with.
     */
    private static final int DEFAULT_MIN_GOALIE_GAMES = 25;

    /**
     * What a team starts on a draft that names no roster: the web's default, which is what such a
     * board was drafted with.
     */
    private static final RosterSlots DEFAULT_ROSTER_SLOTS = new RosterSlots(2, 2, 2, 0, 0, 4, 0, 4, 2);

    private final YahooLeagueDraftService draftService;
    private final YahooLeagueRosterService rosterService;
    private final YahooLeagueService leagueService;
    private final EspnLeagueRosterService espnRosterService;
    private final EspnLeagueService espnLeagueService;
    private final ProjectionSeedService seedService;
    private final ProjectionService projectionService;
    private final PlayerPoolRows poolRows;
    private final PlayerService playerService;
    private final AiProjectionAvailability aiProjection;
    private final LeagueSummaryCalculator calculator;
    private final SeasonScheduleService scheduleService;
    private final int defaultSeason;
    private final String defaultModelVersion;

    public LeagueSummaryService(
            YahooLeagueDraftService draftService,
            YahooLeagueRosterService rosterService,
            YahooLeagueService leagueService,
            EspnLeagueRosterService espnRosterService,
            EspnLeagueService espnLeagueService,
            ProjectionSeedService seedService,
            ProjectionService projectionService,
            PlayerPoolRows poolRows,
            PlayerService playerService,
            AiProjectionAvailability aiProjection,
            LeagueSummaryCalculator calculator,
            SeasonScheduleService scheduleService,
            @Value("${services.projection.season}") int defaultSeason,
            @Value("${services.projection.model-version}") String defaultModelVersion) {
        this.draftService = draftService;
        this.rosterService = rosterService;
        this.leagueService = leagueService;
        this.espnRosterService = espnRosterService;
        this.espnLeagueService = espnLeagueService;
        this.seedService = seedService;
        this.projectionService = projectionService;
        this.poolRows = poolRows;
        this.playerService = playerService;
        this.aiProjection = aiProjection;
        this.calculator = calculator;
        this.scheduleService = scheduleService;
        this.defaultSeason = defaultSeason;
        this.defaultModelVersion = defaultModelVersion;
    }

    /**
     * A league's teams, totalled.
     *
     * @param userId whose Yahoo account the league is read with
     * @param leagueKey the league, which the user must have access to
     * @param source which projection the players are scored against
     * @param projectionId the board, where the source is {@link SummarySource#PROJECTION}
     * @return the summary, with the per-player halves in it
     */
    public Result summarise(String userId, String leagueKey, SummarySource source, UUID projectionId) {
        return summariseLeague(userId, source, projectionId, () -> {
            LeagueDraftResponse draft = draftService.draft(userId, leagueKey);
            LeagueProjectionSettingsResponse settings = leagueService.projectionSettings(userId, leagueKey);
            return new League(
                    teams(draft, currentRosters(userId, leagueKey, draft)),
                    settings,
                    draft.status(),
                    draft.picks().size());
        });
    }

    /**
     * An ESPN league's teams, totalled from what each holds on ESPN today.
     *
     * @param userId whose stored ESPN cookies the league is read with, where it is private
     * @param leagueId ESPN's id for the league
     * @param source which projection the players are scored against
     * @param projectionId the board, where the source is {@link SummarySource#PROJECTION}
     * @return the summary, with the per-player halves in it
     */
    public Result summariseEspn(String userId, String leagueId, SummarySource source, UUID projectionId) {
        return summariseLeague(userId, source, projectionId, () -> {
            EspnLeagueRosterService.Rosters rosters = espnRosterService.rosters(userId, leagueId);
            LeagueProjectionSettingsResponse settings = espnLeagueService.projectionSettings(userId, leagueId);
            List<LeagueSummaryCalculator.TeamPicks> teams = rosters.teams().stream()
                    .map(team -> new LeagueSummaryCalculator.TeamPicks(
                            team.id(), team.name(), team.mine(), rosters.players().get(team.id())))
                    .toList();
            int rostered = teams.stream().mapToInt(team -> team.playerIds().size()).sum();
            return new League(teams, settings, rosters.status(), rostered);
        });
    }

    /**
     * What a platform's league brings to its totals.
     *
     * @param picks how many players its teams hold between them, which is nothing before the draft
     */
    private record League(
            List<LeagueSummaryCalculator.TeamPicks> teams,
            LeagueProjectionSettingsResponse settings,
            LeagueDraftStatus status,
            int picks) {
    }

    private Result summariseLeague(
            String userId, SummarySource source, UUID projectionId, Supplier<League> read) {
        if (source == SummarySource.MODEL && !aiProjection.available()) {
            throw new NoSuchElementException("The AI projection is not enabled");
        }
        // The board is read first: one the user may not read is a 404 before the platform is asked
        // anything.
        ProjectionResponse board = source == SummarySource.PROJECTION
                ? projectionService.get(UUID.fromString(userId), projectionId)
                : null;
        League platformLeague = read.get();
        LeagueProjectionSettingsResponse settings = platformLeague.settings();

        ProjectionSeedService.Seed inSeason = inSeason(source);
        List<ScoredPlayer> pool = pool(source, board, inSeason);
        List<LeagueSummaryCalculator.TeamPicks> teams = platformLeague.teams();
        LeagueScoring league = scoring(settings, teams.size());

        LeagueSummary summary = calculator.summarise(pool, teams, league, scheduleService.schedule());
        return new Result(
                summary,
                source,
                source == SummarySource.MODEL ? defaultModelVersion : null,
                board == null ? null : board.id(),
                settings.scoringType(),
                platformLeague.status(),
                platformLeague.picks(),
                unprojected(pool, teams),
                inSeason != null);
    }

    /**
     * A draft made here, its teams totalled from their picks.
     *
     * @param userId whose draft it is
     * @param draftId the draft, one of the user's own
     * @param source which projection the players are scored against
     * @param projectionId the board, where the source is {@link SummarySource#PROJECTION}
     * @return the summary, with the per-player halves in it
     * @throws NoSuchElementException where the id is not a draft, or the model was asked for and is
     *     off
     */
    public Result summariseDraft(String userId, UUID draftId, SummarySource source, UUID projectionId) {
        if (source == SummarySource.MODEL && !aiProjection.available()) {
            throw new NoSuchElementException("The AI projection is not enabled");
        }
        UUID user = UUID.fromString(userId);
        ProjectionResponse stored = projectionService.get(user, draftId);
        DraftState draft = stored.data() == null ? null : stored.data().draft();
        if (stored.kind() != ProjectionKind.DRAFT || draft == null) {
            throw new NoSuchElementException("Not a draft");
        }
        ProjectionResponse board = source == SummarySource.PROJECTION
                ? projectionService.get(user, projectionId)
                : null;

        ProjectionSeedService.Seed inSeason = inSeason(source);
        List<ScoredPlayer> pool = pool(source, board, inSeason);
        List<LeagueSummaryCalculator.TeamPicks> teams = teams(draft);
        LeagueScoring league = scoring(draft.settings(), stored.data().settings(), teams.size());

        LeagueSummary summary = calculator.summarise(pool, teams, league, scheduleService.schedule());
        int picks = draft.picks() == null ? 0 : draft.picks().size();
        return new Result(
                summary,
                source,
                source == SummarySource.MODEL ? defaultModelVersion : null,
                board == null ? null : board.id(),
                league.points() ? ScoringBasis.POINTS : ScoringBasis.CATEGORY,
                draftStatus(draft, picks),
                picks,
                unprojected(pool, teams),
                inSeason != null);
    }

    private static LeagueDraftStatus draftStatus(DraftState draft, int picks) {
        if (draft.finishedAt() != null) {
            return LeagueDraftStatus.FINISHED;
        }
        return picks == 0 ? LeagueDraftStatus.PRE_DRAFT : LeagueDraftStatus.IN_PROGRESS;
    }

    /**
     * @param summary the teams and their totals
     * @param source which projection they were scored against
     * @param modelVersion the model's version where it was the model, null otherwise
     * @param projectionId the board's id where it was a board, null otherwise
     * @param scoringType how the league scores, which is what its totals are in
     * @param status where the league's draft has got to
     * @param picks how many picks its draft has made, so a league yet to draft can say so rather
     *     than showing every team at nothing
     * @param unprojectedPlayers how many of the teams' players have no line to be scored by
     * @param inSeason whether the model's lines were its in-season ones: each player's rest of
     *     the season, lifted, rather than the season line it was drafted on
     */
    public record Result(
            LeagueSummary summary,
            SummarySource source,
            String modelVersion,
            String projectionId,
            ScoringBasis scoringType,
            LeagueDraftStatus status,
            int picks,
            int unprojectedPlayers,
            boolean inSeason) {
    }

    /**
     * The teams' players that the pool has no line for. They count for nothing, which is right for
     * the model's pool, but on a board that left players out it is the board speaking, and the
     * page has to say so.
     */
    private static int unprojected(List<ScoredPlayer> pool, List<LeagueSummaryCalculator.TeamPicks> teams) {
        Set<Integer> projected = pool.stream().map(ScoredPlayer::playerId).collect(Collectors.toSet());
        return (int) teams.stream()
                .flatMap(team -> team.playerIds().stream())
                .filter(playerId -> !projected.contains(playerId))
                .distinct()
                .count();
    }

    /** The rows the teams are scored against, for the whole pool rather than the rostered players. */
    /**
     * The model's in-season lines, once the season is under way: each player's rest of the
     * season from the last nightly run, lifted to the season line's scale, so a team is ranked on
     * what its players will do from here: not on goals they scored for another team (the whole
     * season, 2026-10-01 to 2026-10-02), nor on what the model said of them on opening night. The
     * rest's expectation (its lines until 2026-10-01) sat beside a lifted season line, and read
     * that way every star dropped 15-20% a game into the season. Null for any other
     * source — a board and last season's stats are ranked as they are, whole seasons, at any point
     * in the season (Alexander's call, 2026-09-29) — and while the season has no rest to project.
     */
    private ProjectionSeedService.Seed inSeason(SummarySource source) {
        return source == SummarySource.MODEL ? seedService.inSeason(defaultSeason).orElse(null) : null;
    }

    private List<ScoredPlayer> pool(
            SummarySource source, ProjectionResponse board, ProjectionSeedService.Seed inSeason) {
        Map<Integer, Identity> identities = identities();
        List<PlayerProjection> rows = switch (source) {
            case MODEL -> inSeason == null
                    ? modelRows()
                    : inSeason.players().stream().map(PlayerProjection::from).toList();
            case LAST_SEASON -> lastSeasonRows();
            case PROJECTION -> {
                overridePositions(identities, board.data().positionOverrides());
                yield board.data().players();
            }
        };
        List<ScoredPlayer> pool = new ArrayList<>(rows.size());
        for (PlayerProjection row : rows) {
            Identity identity = identities.get(row.playerId());
            if (identity == null) {
                // A line for someone the pool does not carry cannot be drafted or named, and a
                // z-score pool of players nobody can pick would move everyone else's numbers.
                continue;
            }
            pool.add(ScoredPlayer.of(row, identity.name(), identity.team(), identity.positions()));
        }
        return pool;
    }

    private List<PlayerProjection> modelRows() {
        return seedService.seed(defaultSeason, defaultModelVersion).players().stream()
                .map(PlayerProjection::from)
                .toList();
    }

    private List<PlayerProjection> lastSeasonRows() {
        return poolRows.read().all(false).stream().map(PlayerProjection::from).toList();
    }

    private record Identity(String name, String team, Set<String> positions) {
    }

    /**
     * The positions the board's owner set by hand, over the ones the pool reports: the lineup a
     * team fills should be the one the board itself would fill.
     */
    private static void overridePositions(Map<Integer, Identity> identities, List<PositionOverride> overrides) {
        if (overrides == null) {
            return;
        }
        for (PositionOverride override : overrides) {
            Identity identity = identities.get(override.playerId());
            if (identity == null || identity.positions().contains("G")) {
                continue;
            }
            Set<String> positions = override.positions().stream()
                    .map(Enum::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            identities.put(override.playerId(), new Identity(identity.name(), identity.team(), positions));
        }
    }

    private Map<Integer, Identity> identities() {
        Map<Integer, Identity> identities = new HashMap<>();
        for (SkaterResponse skater : playerService.getSkaters()) {
            Set<String> positions = skater.positions().stream()
                    .map(Enum::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            identities.put(skater.id(), new Identity(skater.name(), skater.teamAbbrev(), positions));
        }
        for (GoalieResponse goalie : playerService.getGoalies()) {
            identities.put(goalie.id(), new Identity(goalie.name(), goalie.teamAbbrev(), Set.of("G")));
        }
        return identities;
    }

    /**
     * Each team's players today by Yahoo's team key, or null where the draft's picks are what the
     * teams hold: before the draft is over, and in a league whose rosters Yahoo lists all empty.
     */
    private Map<String, List<Integer>> currentRosters(String userId, String leagueKey, LeagueDraftResponse draft) {
        if (draft.status() == LeagueDraftStatus.PRE_DRAFT || draft.status() == LeagueDraftStatus.IN_PROGRESS) {
            return null;
        }
        Map<String, List<Integer>> rosters = rosterService.rosters(userId, leagueKey);
        boolean anyRostered = rosters.values().stream().anyMatch(players -> !players.isEmpty());
        return anyRostered ? rosters : null;
    }

    /**
     * The league's teams with their players: the roster each holds today where there is one,
     * otherwise the players it drafted, in pick order. A player on a team the league does not list
     * is dropped rather than credited to anyone.
     */
    private List<LeagueSummaryCalculator.TeamPicks> teams(
            LeagueDraftResponse draft, Map<String, List<Integer>> rosters) {
        Map<String, List<Integer>> playersByTeam = new LinkedHashMap<>();
        for (LeagueDraftTeam team : draft.teams()) {
            playersByTeam.put(team.id(), new ArrayList<>());
        }
        if (rosters != null) {
            rosters.forEach((teamId, players) -> {
                List<Integer> held = playersByTeam.get(teamId);
                if (held != null) {
                    held.addAll(players);
                }
            });
        } else {
            for (LeagueDraftPick pick : draft.picks()) {
                List<Integer> picks = playersByTeam.get(pick.teamId());
                if (picks != null) {
                    picks.add(pick.playerId());
                }
            }
        }
        return draft.teams().stream()
                .map(team -> new LeagueSummaryCalculator.TeamPicks(
                        team.id(),
                        team.name(),
                        team.mine(),
                        List.copyOf(playersByTeam.get(team.id()))))
                .toList();
    }

    /**
     * A draft's teams in its draft order, each holding the players it picked. A pick for a team the
     * draft does not list is dropped rather than credited to anyone.
     */
    private static List<LeagueSummaryCalculator.TeamPicks> teams(DraftState draft) {
        List<DraftTeam> listed = draft.teams() == null ? List.of() : draft.teams();
        Map<String, DraftTeam> byId = new LinkedHashMap<>();
        for (DraftTeam team : listed) {
            byId.put(team.id(), team);
        }
        Map<String, DraftTeam> ordered = new LinkedHashMap<>();
        for (String teamId : draft.order() == null ? List.<String>of() : draft.order()) {
            DraftTeam team = byId.get(teamId);
            if (team != null) {
                ordered.putIfAbsent(teamId, team);
            }
        }
        byId.forEach(ordered::putIfAbsent);

        Map<String, List<Integer>> playersByTeam = new LinkedHashMap<>();
        ordered.keySet().forEach(teamId -> playersByTeam.put(teamId, new ArrayList<>()));
        for (DraftPick pick : draft.picks() == null ? List.<DraftPick>of() : draft.picks()) {
            List<Integer> picked = playersByTeam.get(pick.teamId());
            if (picked != null) {
                picked.add(pick.playerId());
            }
        }
        return ordered.values().stream()
                .map(team -> new LeagueSummaryCalculator.TeamPicks(
                        team.id(), team.name(), team.mine(), List.copyOf(playersByTeam.get(team.id()))))
                .toList();
    }

    /**
     * How a draft scores: the league it was set up with, or, on a draft saved before drafts held a
     * league, its projection's, the same fallback the board ranks by. Its size is its team count
     * where the league does not say, as a Yahoo league's is.
     */
    private static LeagueScoring scoring(DraftSettings league, ProjectionSettings fallback, int teamCount) {
        int teams = Math.max(2, teamCount);
        if (league != null) {
            return LeagueScoring.of(
                    league.scoringType() != ProjectionSettings.ScoringType.CATEGORY,
                    league.statWeights(),
                    league.activeScoringColumns(),
                    league.rosterSlots() == null ? DEFAULT_ROSTER_SLOTS : league.rosterSlots(),
                    league.leagueSize() == null ? teams : league.leagueSize(),
                    league.minGoalieGames() == null ? DEFAULT_MIN_GOALIE_GAMES : league.minGoalieGames(),
                    null);
        }
        return LeagueScoring.of(
                fallback.scoringType() != ProjectionSettings.ScoringType.CATEGORY,
                fallback.statWeights(),
                fallback.activeScoringColumns(),
                fallback.rosterSlots() == null ? DEFAULT_ROSTER_SLOTS : fallback.rosterSlots(),
                fallback.leagueSize() == null ? teams : fallback.leagueSize(),
                fallback.minGoalieGames() == null ? DEFAULT_MIN_GOALIE_GAMES : fallback.minGoalieGames(),
                null);
    }

    /**
     * How the league scores, as the league itself states it. Its size is its team count where
     * Yahoo does not say, which is the number the two z-score pools are sized from.
     */
    static LeagueScoring scoring(LeagueProjectionSettingsResponse settings, int teamCount) {
        Integer leagueSize = settings.leagueSize();
        return LeagueScoring.of(
                settings.scoringType() == ScoringBasis.POINTS,
                settings.statWeights(),
                settings.activeScoringColumns(),
                settings.rosterSlots(),
                leagueSize == null || leagueSize < 2 ? Math.max(2, teamCount) : leagueSize,
                DEFAULT_MIN_GOALIE_GAMES,
                null);
    }
}
