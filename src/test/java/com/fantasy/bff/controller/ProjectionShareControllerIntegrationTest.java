package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.generated.db.model.CreateShareRequest;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.ShareResponse;
import com.fantasy.bff.generated.db.model.SharedPlayer;
import com.fantasy.bff.generated.db.model.SharedProjectionData;
import com.fantasy.bff.generated.db.model.SharedProjectionResponse;
import com.fantasy.bff.dto.response.RookiesResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import com.fantasy.bff.service.RookieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientResponseException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectionShareControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private DatabaseServiceClient databaseServiceClient;

    @MockitoBean
    private RookieService rookieService;

    /**
     * Nobody can say who is a rookie unless a test says otherwise, which is the answer every
     * environment without the projection service gives, production included.
     */
    @BeforeEach
    void rookieStatusIsUnknownByDefault() {
        when(rookieService.rookies()).thenReturn(RookiesResponse.unknown());
    }

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String TOKEN = "s0mErAnd0mT0k3nV4lu3ab";
    private static final String SHARE_PATH = "/api/v1/projections/" + PROJECTION_ID + "/share";

    private static final String VALID_BODY = """
            {
              "players": [
                { "playerId": 1, "name": "Connor McDavid", "teamAbbrev": "EDM", "positions": ["C"],
                  "type": "skater", "rank": 1, "value": 412.5,
                  "stats": { "utility": { "gp": 82 }, "scoring": { "goals": 64 } } }
              ]
            }
            """;

    private String token() {
        return jwtTokenValidator.generateToken(USER_ID.toString(), "owner@example.com");
    }

    private static ShareResponse shareResponse() {
        return new ShareResponse()
                .id("33333333-3333-3333-3333-333333333333")
                .projectionId(PROJECTION_ID.toString())
                .token(TOKEN)
                .createdAt(OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.of(2026, 8, 2, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    private static SharedProjectionResponse sharedProjection(String name, String username) {
        return sharedProjection(name, username, 1);
    }

    /** A published board of {@code rows} rows, the first of which is always McDavid at rank 1. */
    private static SharedProjectionResponse sharedProjection(String name, String username, int rows) {
        List<SharedPlayer> board = IntStream.rangeClosed(1, rows)
                .mapToObj(rank -> new SharedPlayer()
                        .playerId(rank)
                        .name(rank == 1 ? "Connor McDavid" : "Player " + rank)
                        .teamAbbrev("EDM")
                        .positions(List.of("C"))
                        .type(SharedPlayer.TypeEnum.SKATER)
                        .rank(rank)
                        .value(500.0 - rank)
                        .stats(new PlayerStats()
                                .utility(Map.of("gp", 82.0))
                                .scoring(Map.of("goals", 64.0))))
                .toList();
        return new SharedProjectionResponse()
                .token(TOKEN)
                .name(name)
                .authorUsername(username)
                .season(SharedProjectionResponse.SeasonEnum._20262027)
                .data(new SharedProjectionData()
                        .projectionSettings(new ProjectionSettings()
                                .scoringType(ProjectionSettings.ScoringTypeEnum.POINTS)
                                .statWeights(Map.of("goals", 4.5))
                                .activeScoringColumns(List.of("goals"))
                                .activeUtilityColumns(List.of("gp"))
                                .scaleSettings(Map.of())
                                .decimalSettings(Map.of("goals", 0))
                                .useDefaultDecimals(true)
                                .leagueSize(12))
                        .players(board))
                .createdAt(OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.of(2026, 8, 2, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void share_withValidToken_returnsTheLinkToPost() throws Exception {
        when(databaseServiceClient.shareProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(shareResponse());

        mockMvc.perform(put(SHARE_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(TOKEN))
                .andExpect(jsonPath("$.shareUrl").value("http://localhost:4200/s/" + TOKEN))
                .andExpect(jsonPath("$.viewCount").doesNotExist());
    }

    @Test
    void share_forwardsTheRowsAndAliasDownstream() throws Exception {
        when(databaseServiceClient.shareProjection(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(shareResponse());

        mockMvc.perform(put(SHARE_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateShareRequest> request = ArgumentCaptor.forClass(CreateShareRequest.class);
        verify(databaseServiceClient).shareProjection(eq(USER_ID), eq(PROJECTION_ID), request.capture());
        assertThat(request.getValue().getPlayers()).hasSize(1);
        assertThat(request.getValue().getPlayers().getFirst().getName()).isEqualTo("Connor McDavid");
    }

    @Test
    void share_withoutToken_isUnauthorizedAndNeverReachesDownstream() throws Exception {
        mockMvc.perform(put(SHARE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verify(databaseServiceClient, never()).shareProjection(any(), any(), any());
    }

    @Test
    void publicRead_needsNoSignIn() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex"));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My league"))
                .andExpect(jsonPath("$.authorUsername").value("Alex"))
                .andExpect(jsonPath("$.data.players[0].name").value("Connor McDavid"));
    }

    @Test
    void publicRead_withoutSignIn_stopsAtTheTopOfTheBoard() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex", 400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(25))
                .andExpect(jsonPath("$.totalPlayers").value(400))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    /**
     * A board whose best players sit at the bottom of the published ranking: goals climb with the
     * rank, and the defencemen are every even row. A preview cut before the sort was applied could
     * not answer either question correctly, which is the point. Three teams, cycling on a
     * different period to the positions so that a team filter cannot pass by matching one.
     */
    private static SharedProjectionResponse boardOrderedAgainstItself(int rows) {
        SharedProjectionResponse shared = sharedProjection("My league", "Alex", rows);
        List<String> teams = List.of("EDM", "COL", "TOR");
        List<SharedPlayer> board = IntStream.rangeClosed(1, rows)
                .mapToObj(rank -> new SharedPlayer()
                        .playerId(rank)
                        .name("Player " + rank)
                        .teamAbbrev(teams.get(rank % 3))
                        .positions(List.of(rank % 2 == 0 ? "D" : "C"))
                        .type(SharedPlayer.TypeEnum.SKATER)
                        .rank(rank)
                        .value(500.0 - rank)
                        .stats(new PlayerStats()
                                .utility(Map.of("gp", 82.0))
                                .scoring(Map.of("goals", (double) rank))))
                .toList();
        shared.getData().players(board);
        return shared;
    }

    @Test
    void publicRead_withoutSignIn_sortsTheWholeBoardBeforeTakingItsTop() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN)
                        .param("sort", "goals")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(25))
                .andExpect(jsonPath("$.data.players[0].name").value("Player 400"))
                .andExpect(jsonPath("$.totalPlayers").value(400))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void publicRead_withoutSignIn_filtersTheWholeBoardBeforeTakingItsTop() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN).param("position", "D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(25))
                .andExpect(jsonPath("$.data.players[0].name").value("Player 2"))
                .andExpect(jsonPath("$.data.players[1].name").value("Player 4"));
    }

    /** The gate is about the reader, not the filter: a board longer than the preview is cut. */
    @Test
    void publicRead_withoutSignIn_staysTruncatedUnderAFilterThatMatchesFewRows() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN).param("position", "G"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(0))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void publicRead_withAnUnknownColumn_keepsThePublishedOrder() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN).param("sort", "not-a-stat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players[0].name").value("Player 1"));
    }

    @Test
    void publicRead_withoutSignIn_searchesTheWholeBoardBeforeTakingItsTop() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN).param("search", "player 399"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(1))
                .andExpect(jsonPath("$.data.players[0].name").value("Player 399"));
    }

    @Test
    void publicRead_withoutSignIn_filtersTheWholeBoardByTeam() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN).param("team", "COL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(25))
                .andExpect(jsonPath("$.data.players[0].name").value("Player 1"))
                .andExpect(jsonPath("$.data.players[1].name").value("Player 4"));
    }

    @Test
    void publicRead_withoutSignIn_keepsOnlyTheRookiesWhenAsked() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));
        when(rookieService.rookies()).thenReturn(RookiesResponse.of(Set.of(300, 301)));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN).param("rookies", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(2))
                .andExpect(jsonPath("$.data.players[0].name").value("Player 300"));
    }

    /** The filter is only as good as the answer behind it, and there isn't always one. */
    @Test
    void publicRead_withoutSignIn_ignoresTheRookieFilterWhenNobodyCanSayWhoIsOne() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN).param("rookies", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(25))
                .andExpect(jsonPath("$.rookieIds.length()").value(0));
    }

    /** Both controls are filled from the whole board, or they would offer a reader behind the
     * gate only what the rows they were sent happen to contain. */
    @Test
    void publicRead_namesTheTeamsAndRookiesOfTheWholeBoard() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));
        when(rookieService.rookies()).thenReturn(RookiesResponse.of(Set.of(399, 12345)));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams").value(contains("COL", "EDM", "TOR")))
                .andExpect(jsonPath("$.rookieIds").value(contains(399)));
    }

    @Test
    void publicRead_withAnOverlongSearch_isRejected() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex", 400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN).param("search", "x".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    /** Someone holding the whole board sorts it in the browser; the params are not theirs. */
    @Test
    void publicRead_whenSignedIn_ignoresTheOrderAskedForAndSendsEverything() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN)
                        .header("Authorization", "Bearer " + token())
                        .param("sort", "goals")
                        .param("direction", "desc")
                        .param("position", "D")
                        .param("search", "Player 399")
                        .param("team", "COL")
                        .param("rookies", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(400))
                .andExpect(jsonPath("$.data.players[0].name").value("Player 1"));
    }

    @Test
    void publicRead_whenSignedIn_carriesTheWholeBoard() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex", 400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(400))
                .andExpect(jsonPath("$.totalPlayers").value(400))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void publicRead_withoutSignIn_isNotTruncatedWhenTheBoardIsShorterThanThePreview()
            throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex", 10));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(10))
                .andExpect(jsonPath("$.totalPlayers").value(10))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void publicRead_withAnExpiredToken_isTreatedAsAnonymousRatherThanRejected() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex", 400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN)
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(25))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void publicRead_relaysNotFoundForAnUnknownToken() throws Exception {
        when(databaseServiceClient.getSharedProjection("nope"))
                .thenThrow(new RestClientResponseException(
                        "Not Found", HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        mockMvc.perform(get("/api/v1/shared/nope")).andExpect(status().isNotFound());
    }

    @Test
    void preview_servesPerShareOpenGraphTagsForCrawlers() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex"));

        String html = mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/preview"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(
                "og:title\" content=\"My league by Alex - Fantasy Hockey Projections | SlapStat\"");
        assertThat(html).contains("Alex&#39;s NHL projections for the upcoming season. Top players:");
        assertThat(html).contains("Connor McDavid");
        assertThat(html).contains("http://localhost:4200/s/" + TOKEN);
    }

    @Test
    void preview_escapesOwnerSuppliedText() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("<script>alert(1)</script>", "\"onload=\"x"));

        String html = mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/preview"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).doesNotContain("\"onload=\"x");
    }

    @Test
    void preview_pointsAtThePerShareCardRatherThanTheSiteWideImage() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex"));

        String html = mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/preview"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("og:image\" content=\"http://localhost:4200/s/" + TOKEN + "/og-image.png");
        assertThat(html).doesNotContain("content=\"http://localhost:4200/og-image.png\"");
    }

    @Test
    void ogImage_rendersACardForTheShare() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex"));

        byte[] card = mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/og-image.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(ImageIO.read(new ByteArrayInputStream(card)).getWidth()).isEqualTo(1200);
    }

    @Test
    void ogImage_needsNoSignIn() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex"));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/og-image.png")).andExpect(status().isOk());
    }

    @Test
    void preview_doesNotLetAProjectionNameActAsATemplatePlaceholder() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("{{url}}", "Alex"));

        String html = mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/preview"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("&#123;&#123;url}}");
        assertThat(html).doesNotContain("<title>http://localhost:4200/s/");
    }
}
