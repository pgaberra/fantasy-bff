package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.LeagueDraftPick;
import com.fantasy.bff.dto.response.LeagueDraftResponse;
import com.fantasy.bff.dto.response.LeagueDraftStatus;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.dto.response.ScoringBasis;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.dto.response.SummarySource;
import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.RosterSlots;
import com.fantasy.bff.service.scoring.LeagueSummaryCalculator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * What the cold path is and is not allowed to hand back. The arithmetic behind the totals is
 * covered where it lives ({@code service.scoring}); what is decided here is where the league
 * comes from, and how much of the answer each account sees.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeagueSummaryServiceTest {

    private static final String USER = "user-1";
    private static final String LEAGUE = "465.l.12345";

    @Mock private YahooLeagueDraftService draftService;
    @Mock private YahooLeagueService leagueService;
    @Mock private ProjectionSeedService seedService;
    @Mock private PlayerPoolRows poolRows;
    @Mock private PlayerService playerService;
    @Mock private AiProjectionAvailability aiProjection;
    @Mock private EntitlementService entitlementService;

    private LeagueSummaryService service;

    private static PlayerProjection row(int playerId, PlayerProjection.TypeEnum type, Map<String, Double> scoring) {
        return new PlayerProjection()
                .playerId(playerId)
                .type(type)
                .stats(new PlayerStats().utility(Map.of("gp", 82.0)).scoring(scoring));
    }

    private static SkaterResponse skater(int id, String name, SkaterPosition position) {
        return new SkaterResponse(id, name, "TOR", null, null, Set.of(position), null);
    }

    @BeforeEach
    void setUp() {
        service = new LeagueSummaryService(
                draftService, leagueService, seedService, poolRows, playerService, aiProjection,
                entitlementService, new LeagueSummaryCalculator(), 20262027, "v1.2.3");

        when(aiProjection.available()).thenReturn(true);
        when(playerService.getSkaters()).thenReturn(List.of(
                skater(1, "Forward One", SkaterPosition.C),
                skater(2, "Forward Two", SkaterPosition.LW),
                skater(3, "Forward Three", SkaterPosition.RW)));
        when(playerService.getGoalies()).thenReturn(List.<GoalieResponse>of());
        when(seedService.seed(anyInt(), anyString())).thenReturn(new ProjectionSeedService.Seed(
                List.of(
                        row(1, PlayerProjection.TypeEnum.SKATER, Map.of("goals", 40.0)),
                        row(2, PlayerProjection.TypeEnum.SKATER, Map.of("goals", 30.0)),
                        row(3, PlayerProjection.TypeEnum.SKATER, Map.of("goals", 15.0))),
                "v1.2.3", 3, 0, 0, Set.of(), 0));
        when(draftService.draft(USER, LEAGUE)).thenReturn(new LeagueDraftResponse(
                LeagueDraftStatus.FINISHED,
                false,
                List.of(
                        new LeagueDraftTeam("t1", "Mine", true),
                        new LeagueDraftTeam("t2", "Theirs", false)),
                true,
                List.of(
                        new LeagueDraftPick(1, 1, "t1", 1),
                        new LeagueDraftPick(2, 1, "t2", 2),
                        new LeagueDraftPick(3, 2, "t2", 3))));
        when(leagueService.projectionSettings(USER, LEAGUE)).thenReturn(settings(null));
    }

    private LeagueProjectionSettingsResponse settings(Integer leagueSize) {
        return new LeagueProjectionSettingsResponse(
                ScoringBasis.POINTS,
                List.of("goals"),
                List.of("gp"),
                Map.of("goals", 1.0),
                "Beer League",
                new RosterSlots().c(1).lw(1).rw(1).d(0).util(0).bn(1).g(0),
                leagueSize,
                List.of(),
                List.of());
    }

    /** The totals are fantasy points or z-scores depending on the league, and the page has to say
     * which — so the answer travels with them rather than being asked for again. */
    @Test
    @DisplayName("says how the league scores, which is what its totals are in")
    void saysHowTheLeagueScores() {
        when(entitlementService.hasPremiumAccess(USER)).thenReturn(false);

        LeagueSummaryService.Result result = service.summarise(USER, LEAGUE, SummarySource.MODEL);

        assertThat(result.scoringType()).isEqualTo(ScoringBasis.POINTS);
    }

    @Test
    @DisplayName("totals the league's own teams from the league's own picks")
    void totalsTheLeague() {
        when(entitlementService.hasPremiumAccess(USER)).thenReturn(true);

        LeagueSummaryService.Result result = service.summarise(USER, LEAGUE, SummarySource.MODEL);

        assertThat(result.picks()).isEqualTo(3);
        assertThat(result.status()).isEqualTo(LeagueDraftStatus.FINISHED);
        assertThat(result.modelVersion()).isEqualTo("v1.2.3");
        assertThat(result.summary().teams()).extracting(team -> team.teamId())
                .containsExactly("t2", "t1");
        // Two picks worth 45 between them beat one worth 40, and the table is ordered by that.
        assertThat(result.summary().teams().get(0).total()).isCloseTo(45, within(1e-9));
        assertThat(result.summary().teams().get(1).total()).isCloseTo(40, within(1e-9));
    }

    /** The whole point of totalling here: a free account is handed the totals and nothing else. */
    @Test
    @DisplayName("an account without premium gets the totals with no lines behind them")
    void freeAccountGetsAggregatesOnly() {
        when(entitlementService.hasPremiumAccess(USER)).thenReturn(false);

        LeagueSummaryService.Result result = service.summarise(USER, LEAGUE, SummarySource.MODEL);

        assertThat(result.premium()).isFalse();
        assertThat(result.summary().teams()).allSatisfy(team -> {
            assertThat(team.total()).isNotNull();
            assertThat(team.values()).isNotEmpty();
            assertThat(team.roster()).as("no roster rows").isNull();
            assertThat(team.positionPlayers()).as("no lineups").isNull();
        });
    }

    @Test
    @DisplayName("premium gets the players behind each total")
    void premiumGetsThePlayers() {
        when(entitlementService.hasPremiumAccess(USER)).thenReturn(true);

        LeagueSummaryService.Result result = service.summarise(USER, LEAGUE, SummarySource.MODEL);

        assertThat(result.premium()).isTrue();
        assertThat(result.summary().teams()).allSatisfy(team -> {
            assertThat(team.roster()).isNotNull();
            assertThat(team.positionPlayers()).isNotNull();
        });
        assertThat(result.summary().teams().get(0).roster()).extracting(row -> row.name())
                .containsExactly("Forward Two", "Forward Three");
    }

    @Test
    @DisplayName("last season's stats are read from the pool, and the model is left alone")
    void lastSeasonDoesNotTouchTheModel() {
        when(entitlementService.hasPremiumAccess(USER)).thenReturn(false);
        when(poolRows.read()).thenThrow(new IllegalStateException("read"));

        assertThatThrownBy(() -> service.summarise(USER, LEAGUE, SummarySource.LAST_SEASON))
                .isInstanceOf(IllegalStateException.class);
        verify(seedService, never()).seed(anyInt(), anyString());
    }

    @Test
    @DisplayName("the model cannot be asked for where the AI projection is switched off")
    void modelOffIsNotFound() {
        when(aiProjection.available()).thenReturn(false);

        assertThatThrownBy(() -> service.summarise(USER, LEAGUE, SummarySource.MODEL))
                .isInstanceOf(NoSuchElementException.class);
        verify(draftService, never()).draft(anyString(), anyString());
    }

    @Test
    @DisplayName("a pick for a team the league does not list is dropped, not credited to anyone")
    void unknownTeamIsDropped() {
        when(entitlementService.hasPremiumAccess(USER)).thenReturn(true);
        when(draftService.draft(USER, LEAGUE)).thenReturn(new LeagueDraftResponse(
                LeagueDraftStatus.FINISHED,
                false,
                List.of(new LeagueDraftTeam("t1", "Mine", true)),
                true,
                List.of(new LeagueDraftPick(1, 1, "t1", 1), new LeagueDraftPick(2, 1, "ghost", 2))));

        LeagueSummaryService.Result result = service.summarise(USER, LEAGUE, SummarySource.MODEL);

        assertThat(result.summary().teams()).hasSize(1);
        assertThat(result.summary().teams().get(0).total()).isCloseTo(40, within(1e-9));
    }

    /**
     * The z-score pools are sized from the league's size, so a league Yahoo does not count for us
     * is counted from its own teams rather than falling back to a stranger's league.
     */
    @Test
    @DisplayName("a league with no stated size is sized by its teams")
    void leagueSizeFallsBackToTheTeamCount() {
        when(entitlementService.hasPremiumAccess(USER)).thenReturn(false);
        when(leagueService.projectionSettings(USER, LEAGUE)).thenReturn(settings(null));

        LeagueSummaryService.Result result = service.summarise(USER, LEAGUE, SummarySource.MODEL);

        assertThat(result.summary().teams()).hasSize(2);
    }

    @Test
    @DisplayName("a league that has not drafted yet says so, with every team at nothing")
    void preDraftLeague() {
        when(entitlementService.hasPremiumAccess(USER)).thenReturn(false);
        when(draftService.draft(USER, LEAGUE)).thenReturn(new LeagueDraftResponse(
                LeagueDraftStatus.PRE_DRAFT,
                false,
                List.of(new LeagueDraftTeam("t1", "Mine", true)),
                false,
                List.of()));

        LeagueSummaryService.Result result = service.summarise(USER, LEAGUE, SummarySource.MODEL);

        assertThat(result.picks()).isZero();
        assertThat(result.status()).isEqualTo(LeagueDraftStatus.PRE_DRAFT);
        assertThat(result.summary().teams().get(0).total()).isZero();
    }
}
