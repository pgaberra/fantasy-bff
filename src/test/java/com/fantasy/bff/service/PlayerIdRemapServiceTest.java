package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.PlayerIdRemapReport;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.PlayerIdPair;
import com.fantasy.bff.generated.db.model.PlayerIdRemapResponse;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerIdRemapServiceTest {

    private static final int STATS_SEASON = 2025;

    /** Above the "this pool came back short" guard without being a whole league to build. */
    private static final int FILLER = 1200;

    private PlayerServiceClient yahooPlayerClient;
    private EspnServiceClient espnServiceClient;
    private DatabaseServiceClient databaseServiceClient;
    private PlayerIdRemapService service;

    @BeforeEach
    void setUp() {
        yahooPlayerClient = mock(PlayerServiceClient.class);
        espnServiceClient = mock(EspnServiceClient.class);
        databaseServiceClient = mock(DatabaseServiceClient.class);
        service = new PlayerIdRemapService(yahooPlayerClient, espnServiceClient,
                databaseServiceClient, new PlayerIdResolver(), STATS_SEASON);
        when(databaseServiceClient.remapPlayerIds(any(), anyBoolean()))
                .thenReturn(new PlayerIdRemapResponse().dryRun(true).projectionsScanned(3));
        when(yahooPlayerClient.getGoalies()).thenReturn(List.of());
        when(espnServiceClient.goalies(STATS_SEASON)).thenReturn(List.of());
    }

    private static SkaterResponse yahooSkater(int id, String name, String team, Integer jersey) {
        return yahooSkater(id, name, team, jersey, 82);
    }

    private static SkaterResponse yahooSkater(int id, String name, String team, Integer jersey,
                                              int gamesPlayed) {
        return new SkaterResponse(id, name, team, null, jersey, Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(gamesPlayed, 1320),
                        new SkaterResponse.ScoringStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0.0, 0, 0, 0, 0, 0, 0, 0)));
    }

    private static com.fantasy.bff.generated.espn.model.SkaterResponse espnSkater(
            long id, String first, String last, String team, Integer jersey) {
        return new com.fantasy.bff.generated.espn.model.SkaterResponse()
                .id(id).firstName(first).lastName(last).position("C").eligiblePositions(List.of("C"))
                .teamAbbrev(team).sweaterNumber(jersey);
    }

    /**
     * Distinct, letters-only names: the matcher normalises a name down to its letters, so a
     * digit in a filler name would collapse every one of them onto the same key.
     */
    private static String fillerName(int i) {
        return "Filler" + (char) ('a' + i / 676) + (char) ('a' + (i / 26) % 26) + (char) ('a' + i % 26);
    }

    /** Both pools, padded to a credible size with players that match each other one-to-one. */
    private void poolsOf(List<SkaterResponse> yahoo,
                         List<com.fantasy.bff.generated.espn.model.SkaterResponse> espn) {
        List<SkaterResponse> yahooAll = new ArrayList<>(yahoo);
        List<com.fantasy.bff.generated.espn.model.SkaterResponse> espnAll = new ArrayList<>(espn);
        for (int i = 0; i < FILLER; i++) {
            yahooAll.add(yahooSkater(100000 + i, fillerName(i) + " Player", "EDM", i % 99));
            espnAll.add(espnSkater(900000L + i, fillerName(i), "Player", "EDM", i % 99));
        }
        when(yahooPlayerClient.getSkaters()).thenReturn(yahooAll);
        when(espnServiceClient.skaters(STATS_SEASON)).thenReturn(espnAll);
    }

    private List<PlayerIdPair> crosswalkSentToDb() {
        ArgumentCaptor<List<PlayerIdPair>> captor = ArgumentCaptor.captor();
        verify(databaseServiceClient).remapPlayerIds(captor.capture(), anyBoolean());
        return captor.getValue();
    }

    @Test
    void buildsTheCrosswalkFromBothPoolsAndHandsItToTheDatabase() {
        poolsOf(List.of(yahooSkater(6743, "Connor McDavid", "EDM", 97)),
                List.of(espnSkater(3895074L, "Connor", "McDavid", "EDM", 97)));

        PlayerIdRemapReport report = service.remap(true);

        assertThat(crosswalkSentToDb())
                .filteredOn(pair -> pair.getFrom() == 6743)
                .singleElement()
                .satisfies(pair -> assertThat(pair.getTo()).isEqualTo(3895074));
        assertThat(report.matched()).isEqualTo(FILLER + 1);
        assertThat(report.unmatched()).isZero();
        assertThat(report.coverage()).isEqualTo(1.0);
        assertThat(report.applied().getProjectionsScanned()).isEqualTo(3);
    }

    @Test
    void passesTheDryRunFlagThrough() {
        poolsOf(List.of(), List.of());

        service.remap(false);

        verify(databaseServiceClient).remapPlayerIds(any(), org.mockito.ArgumentMatchers.eq(false));
    }

    /**
     * A player only one platform lists has no counterpart to find — but they are also how a
     * broken match is noticed, so they are named rather than counted away.
     */
    @Test
    void namesTheYahooPlayersThatFoundNoCounterpart() {
        poolsOf(List.of(yahooSkater(6743, "Connor McDavid", "EDM", 97),
                        yahooSkater(1, "Nobody Here", "SEA", 44)),
                List.of(espnSkater(3895074L, "Connor", "McDavid", "EDM", 97)));

        PlayerIdRemapReport report = service.remap(true);

        assertThat(report.unmatched()).isEqualTo(1);
        assertThat(report.unmatchedSample()).containsExactly("Nobody Here (SEA, 82 GP)");
        assertThat(crosswalkSentToDb()).noneMatch(pair -> pair.getFrom() == 1);
    }

    @Test
    void refusesToBuildACrosswalkFromAPoolThatCameBackShort() {
        when(yahooPlayerClient.getSkaters())
                .thenReturn(List.of(yahooSkater(6743, "Connor McDavid", "EDM", 97)));
        when(espnServiceClient.skaters(STATS_SEASON))
                .thenReturn(List.of(espnSkater(3895074L, "Connor", "McDavid", "EDM", 97)));

        assertThatThrownBy(() -> service.remap(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("far fewer than a league holds");

        verify(databaseServiceClient, never()).remapPlayerIds(any(), anyBoolean());
    }

    /**
     * The Yahoo pool is a frozen snapshot of everyone who was fantasy-relevant last season, and
     * hundreds of them never got into a game — ESPN lists who is active now, so they correctly
     * have no counterpart. Gating on the whole pool would block a perfectly good crosswalk.
     */
    @Test
    void appliesWhenEveryPlayerThatWentUnmatchedNeverPlayed() {
        List<SkaterResponse> yahoo = new ArrayList<>();
        List<com.fantasy.bff.generated.espn.model.SkaterResponse> espn = new ArrayList<>();
        for (int i = 0; i < FILLER; i++) {
            yahoo.add(yahooSkater(1000 + i, fillerName(i) + " Played", "EDM", i % 99, 82));
            espn.add(espnSkater(900000L + i, fillerName(i), "Played", "EDM", i % 99));
        }
        // A third of the pool again, none of whom ESPN lists and none of whom played.
        for (int i = 0; i < FILLER / 2; i++) {
            yahoo.add(yahooSkater(5000 + i, fillerName(i) + " Benched", "EDM", i % 99, 0));
        }
        when(yahooPlayerClient.getSkaters()).thenReturn(yahoo);
        when(espnServiceClient.skaters(STATS_SEASON)).thenReturn(espn);

        PlayerIdRemapReport report = service.remap(false);

        assertThat(report.coverage()).isLessThan(0.7);
        assertThat(report.coverageOfPlayersWithGames()).isEqualTo(1.0);
        assertThat(report.playersWithGames()).isEqualTo(FILLER);
        verify(databaseServiceClient).remapPlayerIds(any(), org.mockito.ArgumentMatchers.eq(false));
    }

    /**
     * Applying a collapsed match would strand rows a rerun cannot reach, because they come back
     * marked as migrated. A player who played is one a projection has real numbers for.
     */
    @Test
    void refusesToApplyWhenPlayersWhoActuallyPlayedGoUnmatched() {
        List<SkaterResponse> yahoo = new ArrayList<>();
        for (int i = 0; i < FILLER; i++) {
            yahoo.add(yahooSkater(1000 + i, "Yahoo" + fillerName(i) + " Only", "EDM", i % 99));
        }
        when(yahooPlayerClient.getSkaters()).thenReturn(yahoo);
        List<com.fantasy.bff.generated.espn.model.SkaterResponse> espn = new ArrayList<>();
        for (int i = 0; i < FILLER; i++) {
            espn.add(espnSkater(900000L + i, "Espn" + fillerName(i), "Only", "EDM", i % 99));
        }
        when(espnServiceClient.skaters(STATS_SEASON)).thenReturn(espn);

        assertThatThrownBy(() -> service.remap(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("actually played")
                .hasMessageContaining("Refusing to apply");

        verify(databaseServiceClient, never()).remapPlayerIds(any(), anyBoolean());
    }

    /** The dry run is exactly how that collapse is meant to be looked at, so it still runs. */
    @Test
    void stillReportsADryRunWhenTooLittleMatched() {
        List<SkaterResponse> yahoo = new ArrayList<>();
        for (int i = 0; i < FILLER; i++) {
            yahoo.add(yahooSkater(1000 + i, "Yahoo" + fillerName(i) + " Only", "EDM", i % 99));
        }
        when(yahooPlayerClient.getSkaters()).thenReturn(yahoo);
        List<com.fantasy.bff.generated.espn.model.SkaterResponse> espn = new ArrayList<>();
        for (int i = 0; i < FILLER; i++) {
            espn.add(espnSkater(900000L + i, "Espn" + fillerName(i), "Only", "EDM", i % 99));
        }
        when(espnServiceClient.skaters(STATS_SEASON)).thenReturn(espn);

        assertThat(service.remap(true).coverageOfPlayersWithGames()).isZero();
    }

    @Test
    void matchesGoaliesToo() {
        poolsOf(List.of(), List.of());
        when(yahooPlayerClient.getGoalies()).thenReturn(List.of(new GoalieResponse(
                101, "Andrei Vasilevskiy", "TB", null, 88,
                new GoalieResponse.Stats(
                        new GoalieResponse.UtilityStats(58),
                        new GoalieResponse.ScoringStats(0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0)))));
        when(espnServiceClient.goalies(STATS_SEASON)).thenReturn(List.of(
                new com.fantasy.bff.generated.espn.model.GoalieResponse()
                        .id(2976847L).firstName("Andrei").lastName("Vasilevskiy").position("G")
                        .eligiblePositions(List.of("G")).teamAbbrev("TB").sweaterNumber(88)));

        service.remap(true);

        assertThat(crosswalkSentToDb())
                .filteredOn(pair -> pair.getFrom() == 101)
                .singleElement()
                .satisfies(pair -> assertThat(pair.getTo()).isEqualTo(2976847));
    }
}
