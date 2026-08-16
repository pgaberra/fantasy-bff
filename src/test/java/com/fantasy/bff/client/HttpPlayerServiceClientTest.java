package com.fantasy.bff.client;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class HttpPlayerServiceClientTest {

    private static final int STATS_SEASON = 2025;

    private WireMockServer server;
    private HttpPlayerServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
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
}
