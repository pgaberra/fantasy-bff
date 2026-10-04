package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.DraftAnalysisPick;
import com.fantasy.bff.dto.response.DraftAnalysisResponse;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.LeagueDraftResponse;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.dto.response.PlayerProjection;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.service.scoring.DraftGrader;
import com.fantasy.bff.service.scoring.LeagueScoring;
import com.fantasy.bff.service.scoring.ProjectionScoring;
import com.fantasy.bff.service.scoring.ScoredPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Draft Analysis: a league's draft, every pick set against the model's ranking, to say which picks
 * were good and which were reaches — the user's own and everyone else's.
 *
 * <p>The ranking is the model's line on the eve of the season, frozen as
 * {@code model_version=preseason}: what the model said when the league drafted, not what it says
 * after a month of games. Where no such line is stored it falls back to the current season line,
 * and says so. The players are valued the way Team Power Rankings values them, by the league's own
 * scoring over the whole pool ({@link ProjectionScoring}), so a player's worth here is his worth
 * there.
 *
 * <p>As with Team Power Rankings, the teams' sums are everyone's and the per-pick half, which is
 * the model's ranking player by player, is premium's.
 */
@Service
public class DraftAnalysisService {

    private final DraftAnalysisAvailability availability;
    private final YahooLeagueDraftService draftService;
    private final YahooLeagueService leagueService;
    private final ProjectionSeedService seedService;
    private final PlayerService playerService;
    private final EntitlementService entitlementService;
    private final int season;
    private final String defaultModelVersion;

    public DraftAnalysisService(
            DraftAnalysisAvailability availability,
            YahooLeagueDraftService draftService,
            YahooLeagueService leagueService,
            ProjectionSeedService seedService,
            PlayerService playerService,
            EntitlementService entitlementService,
            @Value("${services.projection.season}") int season,
            @Value("${services.projection.model-version}") String defaultModelVersion) {
        this.availability = availability;
        this.draftService = draftService;
        this.leagueService = leagueService;
        this.seedService = seedService;
        this.playerService = playerService;
        this.entitlementService = entitlementService;
        this.season = season;
        this.defaultModelVersion = defaultModelVersion;
    }

    /**
     * A Yahoo league's draft, graded.
     *
     * @param userId whose Yahoo account the league is read with
     * @param leagueKey the league, which the user must have access to
     * @return the picks in order and the teams, best draft first; the per-pick half only for an
     *     account with premium
     */
    public DraftAnalysisResponse yahoo(String userId, String leagueKey) {
        availability.require();
        LeagueDraftResponse draft = draftService.draft(userId, leagueKey);
        LeagueProjectionSettingsResponse settings = leagueService.projectionSettings(userId, leagueKey);

        ProjectionSeedService.Seed seed = seedService.seed(season, FaScoutService.PRESEASON);
        boolean preseason = !seed.players().isEmpty();
        if (!preseason) {
            seed = seedService.seed(season, defaultModelVersion);
        }

        Map<Integer, DraftGrader.Player> directory = directory();
        LeagueScoring league = LeagueSummaryService.scoring(settings, draft.teams().size());
        ProjectionScoring.Scores scores = ProjectionScoring.score(pool(seed, directory), league);
        DraftGrader.Graded graded = DraftGrader.grade(
                scores.values(), directory, draft.picks(), draft.teams(), league.leagueSize(), draft.auction());

        boolean premium = entitlementService.hasPremiumAccess(userId);
        List<DraftAnalysisPick> picks = premium
                ? graded.picks()
                : graded.picks().stream().map(DraftAnalysisPick::withoutModel).toList();
        return new DraftAnalysisResponse(
                draft.status(),
                draft.auction(),
                settings.scoringType(),
                preseason,
                seed.modelVersion(),
                premium,
                graded.teams(),
                picks);
    }

    /**
     * The model's rows for the players the pool carries. A line for someone the pool does not carry
     * cannot have been drafted, and in a category league it would move everyone else's z-score.
     */
    private static List<ScoredPlayer> pool(ProjectionSeedService.Seed seed, Map<Integer, DraftGrader.Player> directory) {
        List<ScoredPlayer> pool = new ArrayList<>(seed.players().size());
        for (var row : seed.players()) {
            PlayerProjection line = PlayerProjection.from(row);
            DraftGrader.Player player = directory.get(line.playerId());
            if (player != null) {
                pool.add(ScoredPlayer.of(line, player.name(), player.club(), player.positions()));
            }
        }
        return pool;
    }

    private Map<Integer, DraftGrader.Player> directory() {
        Map<Integer, DraftGrader.Player> directory = new HashMap<>();
        for (SkaterResponse skater : playerService.getSkaters()) {
            Set<String> positions = skater.positions().stream()
                    .map(Enum::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            directory.put(skater.id(), new DraftGrader.Player(skater.name(), skater.teamAbbrev(), positions));
        }
        for (GoalieResponse goalie : playerService.getGoalies()) {
            directory.put(goalie.id(), new DraftGrader.Player(goalie.name(), goalie.teamAbbrev(), Set.of("G")));
        }
        return directory;
    }
}
