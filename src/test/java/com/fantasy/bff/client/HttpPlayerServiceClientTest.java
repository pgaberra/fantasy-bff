package com.fantasy.bff.client;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.service.HeadshotThumbnailer;
import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class HttpPlayerServiceClientTest {

    private static final int STATS_SEASON = 2025;

    private WireMockServer server;
    private HttpPlayerServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfigs.http11());
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
        client = new HttpPlayerServiceClient(restClient, STATS_SEASON);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void getSkaters_mapsStatsAndMultiPosition() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/skaters")).willReturn(okJson("""
                [{"id":1,"firstName":"Connor","lastName":"McDavid","position":"C",
                  "eligiblePositions":["C","LW"],"teamAbbrev":"EDM","headshot":"u",
                  "gamesPlayed":80,"goals":40,"assists":60,"points":100,"plusMinus":20,"pim":30,
                  "powerPlayGoals":10,"powerPlayPoints":40,"shorthandedGoals":1,"shorthandedPoints":2,
                  "gameWinningGoals":5,"otGoals":2,"shots":300,"shootingPctg":0.133,"avgToi":"22:30",
                  "faceoffWinningPctg":0.51,"hits":50,"blockedShots":20,
                  "totalFaceoffWins":500,"totalFaceoffLosses":480}]
                """)));

        List<SkaterResponse> skaters = client.getSkaters();

        assertThat(skaters).hasSize(1);
        SkaterResponse mcdavid = skaters.getFirst();
        assertThat(mcdavid.name()).isEqualTo("Connor McDavid");
        assertThat(mcdavid.positions()).containsExactlyInAnyOrder(SkaterPosition.C, SkaterPosition.LW);
        assertThat(mcdavid.stats().utility().toiPerGame()).isEqualTo(1350);
        assertThat(mcdavid.stats().scoring().shPct()).isEqualTo(13.3);
        // ppa = powerPlayPoints - powerPlayGoals; sha = shorthandedPoints - shorthandedGoals.
        assertThat(mcdavid.stats().scoring().ppa()).isEqualTo(30);
        assertThat(mcdavid.stats().scoring().sha()).isEqualTo(1);
    }

    @Test
    void getSkaters_fallsBackToNhlPositionWhenEligibleEmpty() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/skaters")).willReturn(okJson("""
                [{"id":2,"firstName":"Cale","lastName":"Makar","position":"D",
                  "eligiblePositions":[],"teamAbbrev":"COL"}]
                """)));

        SkaterResponse makar = client.getSkaters().getFirst();

        assertThat(makar.positions()).containsExactly(SkaterPosition.D);
        // Missing stats default to zero so the UI always gets a complete block.
        assertThat(makar.stats().scoring().goals()).isZero();
        assertThat(makar.stats().utility().gp()).isZero();
    }

    /**
     * The read model holds a line per season now, so the season is not optional — asking for
     * the wrong one, or none, would quietly serve a different year's numbers.
     */
    @Test
    void asksForTheConfiguredStatsSeason() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/skaters")).willReturn(okJson("[]")));

        client.getSkaters();

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/players/skaters"))
                .withQueryParam("season", equalTo(String.valueOf(STATS_SEASON))));
    }

    // The whole point of the parameter is that the rows never leave yahoo-service; sending it
    // when there is none would be a parameter it has to reject rather than a request for all.
    @Test
    void asksYahooServiceForOnlyTheSliceItWants() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/skaters")).willReturn(okJson("[]")));

        client.getSkaters(25);

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/players/skaters"))
                .withQueryParam("season", equalTo(String.valueOf(STATS_SEASON)))
                .withQueryParam("limit", equalTo("25")));
    }

    @Test
    void sendsNoLimitWhenTheWholePoolIsWanted() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/goalies")).willReturn(okJson("[]")));

        client.getGoalies(null);

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/players/goalies"))
                .withQueryParam("limit", absent()));
    }

    @Test
    void getGoalies_mapsStats() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/goalies")).willReturn(okJson("""
                [{"id":101,"firstName":"Igor","lastName":"Shesterkin","position":"G",
                  "eligiblePositions":["G"],"teamAbbrev":"NYR","headshot":"u",
                  "gamesPlayed":58,"gamesStarted":57,"wins":36,"losses":17,"otLosses":4,"shutouts":3,
                  "shotsAgainst":1720,"saves":1565,"goalsAgainst":155,"goalsAgainstAvg":2.671,"savePctg":0.9101}]
                """)));

        List<GoalieResponse> goalies = client.getGoalies();

        assertThat(goalies).hasSize(1);
        GoalieResponse igor = goalies.getFirst();
        assertThat(igor.name()).isEqualTo("Igor Shesterkin");
        assertThat(igor.stats().scoring().gaa()).isEqualTo(2.67);
        assertThat(igor.stats().scoring().svPct()).isEqualTo(0.91);
    }

    /**
     * Yahoo's own image URL points at a multi-megapixel original the browser must never be sent
     * to. The frontend is handed this service's path instead.
     */
    @Test
    void pointsHeadshotsAtThisServiceRatherThanAtYahoo() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/skaters")).willReturn(okJson("""
                [{"id":1,"firstName":"Connor","lastName":"McDavid","position":"C",
                  "eligiblePositions":["C"],"teamAbbrev":"EDM",
                  "headshot":"https://s.yimg.com/xe/i/us/sp/v/nhl_cutout/players_l/10132025/6743.png"}]
                """)));

        assertThat(client.getSkaters().getFirst().headshot()).startsWith("/players/1/headshot");
    }

    /**
     * The picture is cached for a week behind an address that otherwise names only the player, so
     * a reframed or resized avatar would go unseen until the cache let go. Carrying the recipe in
     * the address makes a redrawn avatar a different one to fetch.
     */
    @Test
    void marksTheHeadshotAddressWithTheRecipeItWasDrawnBy() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/skaters")).willReturn(okJson("""
                [{"id":1,"firstName":"Connor","lastName":"McDavid","position":"C",
                  "eligiblePositions":["C"],"teamAbbrev":"EDM",
                  "headshot":"https://s.yimg.com/xe/i/us/sp/v/nhl_cutout/players_l/10132025/6743.png"}]
                """)));

        assertThat(client.getSkaters().getFirst().headshot())
                .isEqualTo("/players/1/headshot?v=" + HeadshotThumbnailer.RECIPE);
    }

    @Test
    void leavesHeadshotUnsetForAPlayerWithoutOne() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/goalies")).willReturn(okJson("""
                [{"id":101,"firstName":"Igor","lastName":"Shesterkin","position":"G",
                  "eligiblePositions":["G"],"teamAbbrev":"NYR"}]
                """)));

        assertThat(client.getGoalies().getFirst().headshot()).isNull();
    }

    @Test
    void getHeadshot_returnsTheThumbnailBytes() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/1/headshot")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "image/png").withBody(PNG_BYTES)));

        assertThat(client.getHeadshot(1)).contains(PNG_BYTES);
    }

    @Test
    void getHeadshot_isEmptyWhenThePlayerHasNoStoredHeadshot() {
        server.stubFor(get(urlPathEqualTo("/api/v1/players/1/headshot"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.getHeadshot(1)).isEmpty();
    }

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
}
