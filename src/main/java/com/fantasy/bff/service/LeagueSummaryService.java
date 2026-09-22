package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.LeagueDraftPick;
import com.fantasy.bff.dto.response.LeagueDraftResponse;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.dto.response.PlayerProjection;
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
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * What a league's draft came to: every team's roster totalled against a projection.
 *
 * <p>This is the cold path — a manager who drafted in Yahoo and has never built a board here.
 * Three things are deliberately <b>not</b> taken from the caller: the picks, the teams and the
 * scoring settings all come from the league itself. A summary computed over rosters the caller
 * chose would be a per-player readout of the projection dressed as an aggregate: ask for a league
 * of one-player teams and every "team total" is one player's value. Reading a real league's draft
 * is the thing being offered, and it is also what keeps the offer from being a way around what
 * the model's lines cost.
 */
@Service
public class LeagueSummaryService {

    /**
     * What a goalie must be projected to play to qualify in a category league. The league does
     * not state it — it is the app's own default, the same number a board here starts with.
     */
    private static final int DEFAULT_MIN_GOALIE_GAMES = 25;

    private final YahooLeagueDraftService draftService;
    private final YahooLeagueService leagueService;
    private final ProjectionSeedService seedService;
    private final PlayerPoolRows poolRows;
    private final PlayerService playerService;
    private final AiProjectionAvailability aiProjection;
    private final EntitlementService entitlementService;
    private final LeagueSummaryCalculator calculator;
    private final int defaultSeason;
    private final String defaultModelVersion;

    public LeagueSummaryService(
            YahooLeagueDraftService draftService,
            YahooLeagueService leagueService,
            ProjectionSeedService seedService,
            PlayerPoolRows poolRows,
            PlayerService playerService,
            AiProjectionAvailability aiProjection,
            EntitlementService entitlementService,
            LeagueSummaryCalculator calculator,
            @Value("${services.projection.season}") int defaultSeason,
            @Value("${services.projection.model-version}") String defaultModelVersion) {
        this.draftService = draftService;
        this.leagueService = leagueService;
        this.seedService = seedService;
        this.poolRows = poolRows;
        this.playerService = playerService;
        this.aiProjection = aiProjection;
        this.entitlementService = entitlementService;
        this.calculator = calculator;
        this.defaultSeason = defaultSeason;
        this.defaultModelVersion = defaultModelVersion;
    }

    /**
     * A league's draft, totalled.
     *
     * @param userId whose Yahoo account the league is read with
     * @param leagueKey the league, which the user must have access to
     * @param source which projection the picks are scored against
     * @return the summary, with the per-player halves in it only for an account that may see them
     */
    public Result summarise(String userId, String leagueKey, SummarySource source) {
        if (source == SummarySource.MODEL && !aiProjection.available()) {
            throw new NoSuchElementException("The AI projection is not enabled");
        }
        LeagueDraftResponse draft = draftService.draft(userId, leagueKey);
        LeagueProjectionSettingsResponse settings = leagueService.projectionSettings(userId, leagueKey);

        List<ScoredPlayer> pool = pool(source);
        List<LeagueSummaryCalculator.TeamPicks> teams = teams(draft);
        LeagueScoring league = scoring(settings, teams.size());

        LeagueSummary summary = calculator.summarise(pool, teams, league);
        // The totals are everyone's; the lines they were reached from are what premium pays for.
        boolean premium = entitlementService.hasPremiumAccess(userId);
        return new Result(
                premium ? summary : summary.aggregatesOnly(),
                source,
                source == SummarySource.MODEL ? defaultModelVersion : null,
                premium,
                draft.status(),
                draft.picks().size());
    }

    /**
     * @param summary the teams and their totals
     * @param source which projection they were scored against
     * @param modelVersion the model's version where it was the model, null otherwise
     * @param premium whether the per-player halves are filled in
     * @param status where the league's draft has got to
     * @param picks how many picks it has made, so a league yet to draft can say so rather than
     *     showing every team at nothing
     */
    public record Result(
            LeagueSummary summary,
            SummarySource source,
            String modelVersion,
            boolean premium,
            com.fantasy.bff.dto.response.LeagueDraftStatus status,
            int picks) {
    }

    /** The rows the picks are scored against, for the whole pool rather than the drafted players. */
    private List<ScoredPlayer> pool(SummarySource source) {
        Map<Integer, Identity> identities = identities();
        List<PlayerProjection> rows = source == SummarySource.MODEL ? modelRows() : lastSeasonRows();
        List<ScoredPlayer> pool = new ArrayList<>(rows.size());
        for (PlayerProjection row : rows) {
            Identity identity = identities.get(row.playerId());
            if (identity == null) {
                // A line for someone the pool does not carry cannot be drafted or named, and a
                // z-score pool of players nobody can pick would move everyone else's numbers.
                continue;
            }
            pool.add(ScoredPlayer.of(row, identity.name(), identity.positions()));
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

    private record Identity(String name, Set<String> positions) {
    }

    private Map<Integer, Identity> identities() {
        Map<Integer, Identity> identities = new HashMap<>();
        for (SkaterResponse skater : playerService.getSkaters()) {
            Set<String> positions = skater.positions().stream()
                    .map(Enum::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            identities.put(skater.id(), new Identity(skater.name(), positions));
        }
        for (GoalieResponse goalie : playerService.getGoalies()) {
            identities.put(goalie.id(), new Identity(goalie.name(), Set.of("G")));
        }
        return identities;
    }

    /**
     * The teams with the players they took, in pick order. A pick whose team the league does not
     * list is dropped rather than credited to anyone.
     */
    private List<LeagueSummaryCalculator.TeamPicks> teams(LeagueDraftResponse draft) {
        Map<String, List<Integer>> picksByTeam = new LinkedHashMap<>();
        for (LeagueDraftTeam team : draft.teams()) {
            picksByTeam.put(team.id(), new ArrayList<>());
        }
        for (LeagueDraftPick pick : draft.picks()) {
            List<Integer> picks = picksByTeam.get(pick.teamId());
            if (picks != null) {
                picks.add(pick.playerId());
            }
        }
        return draft.teams().stream()
                .map(team -> new LeagueSummaryCalculator.TeamPicks(
                        team.id(),
                        team.name(),
                        team.mine(),
                        List.copyOf(picksByTeam.get(team.id()))))
                .toList();
    }

    /**
     * How the league scores, as the league itself states it. Its size is its team count where
     * Yahoo does not say, which is the number the two z-score pools are sized from.
     */
    private LeagueScoring scoring(LeagueProjectionSettingsResponse settings, int teamCount) {
        Integer leagueSize = settings.leagueSize();
        return LeagueScoring.of(
                settings.scoringType() == ScoringBasis.POINTS,
                settings.statWeights(),
                settings.activeScoringColumns(),
                RosterSlots.from(settings.rosterSlots()),
                leagueSize == null || leagueSize < 2 ? Math.max(2, teamCount) : leagueSize,
                DEFAULT_MIN_GOALIE_GAMES,
                null);
    }
}
