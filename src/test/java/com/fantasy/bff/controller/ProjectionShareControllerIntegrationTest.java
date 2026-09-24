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
import com.fantasy.bff.model.downstream.Avatar;
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
import java.util.Optional;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void publicRead_pointsAtTheAuthorsPicture_onlyWhenThereIsOne() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex"));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorAvatar").doesNotExist());

        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex")
                        .authorAvatarUpdatedAt(OffsetDateTime.of(2026, 9, 1, 10, 0, 0, 0, ZoneOffset.UTC)));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorAvatar")
                        .value("/shared/" + TOKEN + "/avatar?v=1788256800"));
    }

    @Test
    void authorAvatar_needsNoSignIn_andIsServedUnderItsOwnType() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        when(databaseServiceClient.findSharedProjectionAuthorAvatar(TOKEN))
                .thenReturn(Optional.of(new Avatar("image/png", png)));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/avatar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(png))
                .andExpect(header().string("X-Robots-Tag", "noindex"));
    }

    @Test
    void authorAvatar_isNotFoundWhenTheAuthorHasNone() throws Exception {
        when(databaseServiceClient.findSharedProjectionAuthorAvatar(TOKEN))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/avatar"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicRead_withoutSignIn_carriesTheWholeBoard() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex", 400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(400))
                .andExpect(jsonPath("$.truncated").doesNotExist())
                .andExpect(jsonPath("$.totalPlayers").doesNotExist());
    }

    @Test
    void publicRead_whenSignedIn_carriesTheWholeBoard() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex", 400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(400));
    }

    @Test
    void publicRead_withAnExpiredToken_isServedRatherThanRejected() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex", 400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN)
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(400));
    }

    /**
     * A board whose published order is the reverse of its goal column, over three teams. A server
     * that still sorted, filtered or cut on the way out would answer the parameters below with
     * something other than the whole board in the order it was published.
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

    /**
     * A page loaded before the gate came down still sends its sort and filters with every read.
     * They are no longer the server's to apply, so the answer is the whole published board.
     */
    @Test
    void publicRead_ignoresTheSortAndFiltersAnOlderPageSends() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN)
                        .param("sort", "goals")
                        .param("direction", "desc")
                        .param("position", "D")
                        .param("search", "x".repeat(101))
                        .param("team", "COL")
                        .param("rookies", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players.length()").value(400))
                .andExpect(jsonPath("$.data.players[0].name").value("Player 1"));
    }

    @Test
    void publicRead_namesTheTeamsAndRookiesOfTheBoard() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));
        when(rookieService.rookies()).thenReturn(RookiesResponse.of(Set.of(399, 12345)));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams").value(contains("COL", "EDM", "TOR")))
                .andExpect(jsonPath("$.rookieIds").value(contains(399)));
    }

    /** Nobody being able to say who is a rookie reads as nobody being one: no filter offered. */
    @Test
    void publicRead_namesNoRookiesWhenNobodyCanSayWhoIsOne() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(boardOrderedAgainstItself(400));

        mockMvc.perform(get("/api/v1/shared/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rookieIds.length()").value(0));
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
        assertThat(html).contains(
                "Fantasy Hockey projection for the 2026&ndash;27 season. Top players: McDavid.");
        assertThat(html).contains("http://localhost:4200/s/" + TOKEN);
    }

    @Test
    void preview_namesTheTopPlayersBySurnameUpToTheCap() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex", 20));

        String html = mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/preview"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Top players: McDavid, 2, 3, 4, 5, 6, 7, 8.");
        assertThat(html).doesNotContain("Connor McDavid");
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

    /**
     * Search crawlers get this document too (nginx routes Googlebot here with the preview bots),
     * and a share is meant for whoever its author sends the link to, not for search results. A
     * canonical link would ask for the opposite of noindex, so there is none.
     */
    @Test
    void preview_keepsTheShareOutOfSearchResults() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex"));

        String html = mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/preview"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<meta name=\"robots\" content=\"noindex\" />");
        assertThat(html).doesNotContain("rel=\"canonical\"");
    }

    @Test
    void ogImage_keepsTheCardOutOfImageSearch() throws Exception {
        when(databaseServiceClient.getSharedProjection(TOKEN))
                .thenReturn(sharedProjection("My league", "Alex"));

        String robots = mockMvc.perform(get("/api/v1/shared/" + TOKEN + "/og-image.png"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Robots-Tag");

        assertThat(robots).isEqualTo("noindex");
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
