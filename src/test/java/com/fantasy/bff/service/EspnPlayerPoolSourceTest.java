package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EspnPlayerPoolSourceTest {

    private static final int STATS_SEASON = 2025;

    private EspnServiceClient espnServiceClient;
    private MockRestServiceServer imageServer;
    private EspnPlayerPoolSource source;

    @BeforeEach
    void setUp() {
        espnServiceClient = mock(EspnServiceClient.class);
        RestClient.Builder builder = RestClient.builder().baseUrl("https://images.test");
        imageServer = MockRestServiceServer.bindTo(builder).build();
        source = new EspnPlayerPoolSource(espnServiceClient, builder.build(), STATS_SEASON);
    }

    private static com.fantasy.bff.generated.espn.model.SkaterResponse mcDavid() {
        return new com.fantasy.bff.generated.espn.model.SkaterResponse()
                .id(3895074L).firstName("Connor").lastName("McDavid").position("C")
                .eligiblePositions(List.of("C")).sweaterNumber(97).teamAbbrev("EDM")
                .headshot("https://a.espncdn.com/combiner/i?img=/i/headshots/nhl/players/full/3895074.png")
                .gamesPlayed(82).goals(48).assists(90).points(138).plusMinus(17).pim(44)
                .powerPlayGoals(13).powerPlayPoints(54).shorthandedGoals(1).shorthandedPoints(2)
                .gameWinningGoals(4).shots(306).shootingPctg(0.1568627450980392).avgToi("23:00")
                .faceoffWinningPctg(0.495).hits(40).blockedShots(30).totalFaceoffWins(457)
                .totalFaceoffLosses(466).hatTricks(3).shifts(1841).timeOnIce(113127);
    }

    private static com.fantasy.bff.generated.espn.model.GoalieResponse vasilevskiy() {
        return new com.fantasy.bff.generated.espn.model.GoalieResponse()
                .id(2976847L).firstName("Andrei").lastName("Vasilevskiy").position("G")
                .eligiblePositions(List.of("G")).sweaterNumber(88).teamAbbrev("TB")
                .headshot("https://a.espncdn.com/combiner/i?img=/i/headshots/nhl/players/full/2976847.png")
                .gamesPlayed(58).gamesStarted(58).wins(39).losses(15).shutouts(2)
                .shotsAgainst(1483).saves(1353).goalsAgainst(132).goalsAgainstAvg(2.30853312)
                .savePctg(0.91233985).overtimeLosses(4).timeOnIce(205845);
    }

    @Test
    void getSkaters_asksForTheSeasonTheAppShows() {
        when(espnServiceClient.skaters(STATS_SEASON)).thenReturn(List.of(mcDavid()));

        assertThat(source.getSkaters()).hasSize(1);
    }

    @Test
    void getSkaters_mapsIdentityAndPositions() {
        when(espnServiceClient.skaters(STATS_SEASON)).thenReturn(List.of(mcDavid()));

        SkaterResponse skater = source.getSkaters().getFirst();

        assertThat(skater.id()).isEqualTo(3895074);
        assertThat(skater.name()).isEqualTo("Connor McDavid");
        assertThat(skater.teamAbbrev()).isEqualTo("EDM");
        assertThat(skater.sweaterNumber()).isEqualTo(97);
        assertThat(skater.positions()).containsExactly(SkaterPosition.C);
    }

    /**
     * Straight to the CDN. Proxying every picture through this service put a page's worth of
     * image requests through one host and the edge started refusing them.
     */
    @Test
    void getSkaters_handsOutEspnsOwnImageUrl() {
        when(espnServiceClient.skaters(STATS_SEASON))
                .thenReturn(List.of(mcDavid(), mcDavid().id(2L).headshot(null)));

        assertThat(source.getSkaters()).extracting(SkaterResponse::headshot)
                .containsExactly(
                        "https://a.espncdn.com/combiner/i?img=/i/headshots/nhl/players/full/3895074.png",
                        null);
    }

    @Test
    void getSkaters_carriesTheStatsThatUsedToBeMatchedInByName() {
        when(espnServiceClient.skaters(STATS_SEASON)).thenReturn(List.of(mcDavid()));

        SkaterResponse.ScoringStats scoring = source.getSkaters().getFirst().stats().scoring();

        assertThat(scoring.hatTricks()).isEqualTo(3);
        assertThat(scoring.shifts()).isEqualTo(1841);
        assertThat(scoring.toi()).isEqualTo(113127);
    }

    @Test
    void getSkaters_derivesTheStatsNoPlatformReports() {
        when(espnServiceClient.skaters(STATS_SEASON)).thenReturn(List.of(mcDavid()));

        SkaterResponse skater = source.getSkaters().getFirst();
        SkaterResponse.ScoringStats scoring = skater.stats().scoring();

        // Power-play and shorthanded assists follow from the points.
        assertThat(scoring.ppa()).isEqualTo(41);
        assertThat(scoring.sha()).isEqualTo(1);
        // Special teams is power play plus shorthanded.
        assertThat(scoring.stpg()).isEqualTo(14);
        assertThat(scoring.stpa()).isEqualTo(42);
        assertThat(scoring.stp()).isEqualTo(56);
        // The stored fraction becomes the percent the UI draws.
        assertThat(scoring.shPct()).isEqualTo(15.7);
        assertThat(skater.stats().utility().toiPerGame()).isEqualTo(1380);
        // A centre scores no defenceman points.
        assertThat(scoring.defPoints()).isZero();
    }

    @Test
    void getSkaters_countsPointsAsDefencemanPointsForADefenceEligibleSkater() {
        when(espnServiceClient.skaters(STATS_SEASON)).thenReturn(List.of(
                mcDavid().position("D").eligiblePositions(List.of("D"))));

        SkaterResponse skater = source.getSkaters().getFirst();

        assertThat(skater.positions()).containsExactly(SkaterPosition.D);
        assertThat(skater.stats().scoring().defPoints()).isEqualTo(138);
    }

    @Test
    void getGoalies_mapsTheLineAndDerivesWinPercentage() {
        when(espnServiceClient.goalies(STATS_SEASON)).thenReturn(List.of(vasilevskiy()));

        GoalieResponse goalie = source.getGoalies().getFirst();
        GoalieResponse.ScoringStats scoring = goalie.stats().scoring();

        assertThat(goalie.name()).isEqualTo("Andrei Vasilevskiy");
        assertThat(goalie.headshot())
                .isEqualTo("https://a.espncdn.com/combiner/i?img=/i/headshots/nhl/players/full/2976847.png");
        assertThat(scoring.w()).isEqualTo(39);
        assertThat(scoring.otl()).isEqualTo(4);
        assertThat(scoring.toi()).isEqualTo(205845);
        assertThat(scoring.gaa()).isEqualTo(2.31);
        assertThat(scoring.svPct()).isEqualTo(0.912);
        // 39 wins out of 58 decisions.
        assertThat(scoring.winPct()).isEqualTo(0.672);
    }

    /** A rookie has identity and no numbers; the pool shows them with a complete block of zeroes. */
    @Test
    void getSkaters_zeroesAPlayerWithNoStatsForThatSeason() {
        when(espnServiceClient.skaters(STATS_SEASON)).thenReturn(List.of(
                new com.fantasy.bff.generated.espn.model.SkaterResponse()
                        .id(5209557L).firstName("Mikulas").lastName("Hovorka").position("D")
                        .eligiblePositions(List.of("D"))));

        SkaterResponse skater = source.getSkaters().getFirst();

        assertThat(skater.stats().utility().gp()).isZero();
        assertThat(skater.stats().scoring().points()).isZero();
        assertThat(skater.stats().scoring().toi()).isZero();
        assertThat(skater.headshot()).isNull();
    }

    @Test
    void getHeadshot_readsThePlayersPictureFromEspnsCdn() {
        imageServer.expect(requestTo(containsString("/i/headshots/nhl/players/full/3895074.png")))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_PNG));

        assertThat(source.getHeadshot(3895074)).contains(new byte[] {1, 2, 3});
        imageServer.verify();
    }

    @Test
    void getHeadshot_isEmptyWhenEspnHasNoPictureForThem() {
        imageServer.expect(requestTo(containsString("/i/headshots")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(source.getHeadshot(999)).isEmpty();
    }

    @Test
    void lastSyncedAt_readsWhenEspnServiceLastRefreshedThePool() {
        OffsetDateTime syncedAt = OffsetDateTime.parse("2026-08-25T07:45:00Z");
        when(espnServiceClient.lastPlayerSync()).thenReturn(
                new com.fantasy.bff.generated.espn.model.PlayerSyncStatusResponse()
                        .syncedAt(syncedAt).players(1686L));

        assertThat(source.lastSyncedAt()).contains(syncedAt);
    }

    @Test
    void lastSyncedAt_isEmptyBeforeTheFirstSync() {
        when(espnServiceClient.lastPlayerSync()).thenReturn(
                new com.fantasy.bff.generated.espn.model.PlayerSyncStatusResponse().players(0L));

        assertThat(source.lastSyncedAt()).isEmpty();
    }
}
