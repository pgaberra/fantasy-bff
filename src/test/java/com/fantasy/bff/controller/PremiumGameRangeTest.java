package com.fantasy.bff.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.request.GameRange;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import com.fantasy.bff.generated.projection.model.GoalieSplitResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterSplitResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Choosing the stretch of schedule Who's hot measures is what premium pays for; the last ten
 * games are free. The web draws the same line, but it draws it in a browser — a request that
 * skips the UI has to meet the same answer here.
 *
 * <p>Payments are switched on throughout, since that is the only state in which anything is
 * held back. {@link PremiumGameRangeUnsoldTest} covers the other one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "payments.enabled=true",
        "payments.provider=mock",
        "payments.mock.webhook-secret=test-mock-secret",
        "security.projection-model-enabled=true",
        "services.projection.player-mapping-ttl-ms=0"
})
class PremiumGameRangeTest extends BaseIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private DatabaseServiceClient databaseServiceClient;
    @MockitoBean private ProjectionServiceClient projectionServiceClient;
    @MockitoBean private PlayerServiceClient playerServiceClient;
    /** Keeps the ESPN stat-line enrichment off the network; unstubbed means "no ESPN stats". */
    @MockitoBean private EspnServiceClient espnServiceClient;

    private String token;

    @BeforeEach
    void setUp() {
        token = jwtTokenValidator.generateToken(USER_ID.toString(), "owner@example.com");
    }

    private void withoutASubscription() {
        when(databaseServiceClient.getSubscription(USER_ID)).thenReturn(Optional.empty());
    }

    private void withPremium() {
        when(databaseServiceClient.getSubscription(USER_ID)).thenReturn(Optional.of(
                new SubscriptionResponse()
                        .status(SubscriptionResponse.StatusEnum.ACTIVE)
                        .premium(true)
                        .cancelAtPeriodEnd(false)
                        .provider("mock")
                        .currentPeriodEnd(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))));
    }

    /** Enough of the player pool for a split to be named and served. */
    private void withOneSkaterAndOneGoalie() {
        SkaterSplitResponse skater = new SkaterSplitResponse();
        skater.setNhlId(8478402);
        skater.setSeason(2025);
        skater.setGames(10);
        skater.setPoints(19);
        when(projectionServiceClient.skaterSplits(anyInt(), any(GameRange.class), anyInt()))
                .thenReturn(List.of(skater));
        when(projectionServiceClient.goalieSplits(anyInt(), any(GameRange.class), anyInt()))
                .thenReturn(List.of(new GoalieSplitResponse()));
        when(playerServiceClient.getSkaters(nullable(Integer.class)))
                .thenReturn(List.of(new SkaterResponse(5000, "Connor McDavid", "EDM", null, 97, Set.of(), null)));
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        PlayerResponse mcdavid = new PlayerResponse();
        mcdavid.setNhlId(8478402);
        mcdavid.setFullName("Connor McDavid");
        mcdavid.setCurrentTeam("EDM");
        mcdavid.setSweaterNumber(97);
        mcdavid.setIsActive(true);
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(mcdavid));
    }

    @Test
    @DisplayName("a free account still gets the last ten games, which is the whole free tier")
    void freeRange_isServed() throws Exception {
        withoutASubscription();
        withOneSkaterAndOneGoalie();

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=73&toGame=82")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(5000));
    }

    /**
     * The range is judged before the subscription is looked up, so the request the web makes on
     * every visit by a free account costs nothing downstream.
     */
    @Test
    @DisplayName("and serving it never asks db-service about a subscription")
    void freeRange_doesNotReadTheSubscription() throws Exception {
        withOneSkaterAndOneGoalie();

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=73&toGame=82")
                .header("Authorization", "Bearer " + token));

        verifyNoInteractions(databaseServiceClient);
    }

    @Test
    @DisplayName("the same window said the other way round is the same window")
    void freeRange_asLastGames_isServed() throws Exception {
        withoutASubscription();
        withOneSkaterAndOneGoalie();

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?lastGames=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * A shorter window inside the free one reveals no game the account is not entitled to, and
     * refusing it would turn any off-by-one between client and server into a dead page.
     */
    @Test
    @DisplayName("a narrower window inside the free one passes too")
    void narrowerRange_isServed() throws Exception {
        withoutASubscription();
        withOneSkaterAndOneGoalie();

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=78&toGame=82")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("reaching further back than the last ten is refused")
    void earlierRange_isForbidden() throws Exception {
        withoutASubscription();

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=1&toGame=41")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PREMIUM_REQUIRED"));
    }

    /**
     * The refusal lands before the range is measured: a leaderboard nobody is entitled to costs
     * neither the projection service nor the player mapping behind it.
     */
    @Test
    @DisplayName("and the refusal never reaches the projection service")
    void earlierRange_doesNotMeasureAnything() throws Exception {
        withoutASubscription();

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=1&toGame=41")
                .header("Authorization", "Bearer " + token));

        verifyNoInteractions(projectionServiceClient);
    }

    @Test
    @DisplayName("a window of the right length but the wrong ten games is refused")
    void tenEarlierGames_areForbidden() throws Exception {
        withoutASubscription();

        // Ten games is the free *length*, but games 1-10 are not the free *stretch*: which
        // ten you get to look at is the thing being sold.
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=1&toGame=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the lastGames shorthand cannot reach further back either")
    void longerLastGames_isForbidden() throws Exception {
        withoutASubscription();

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?lastGames=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * Asking for nothing means the whole season, which is a premium range like any other. It is
     * the one refusal a caller can hit without naming a range at all.
     */
    @Test
    @DisplayName("and neither can asking for no range, which means the whole season")
    void openRange_isForbidden() throws Exception {
        withoutASubscription();

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the goalie half is gated on the same line, not left as a way round it")
    void goalies_areGatedToo() throws Exception {
        withoutASubscription();

        mockMvc.perform(get("/api/v1/projection-model/splits/goalies?fromGame=1&toGame=41")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a premium account gets any stretch of the season it asks for")
    void premium_getsTheWholeSeason() throws Exception {
        withPremium();
        withOneSkaterAndOneGoalie();

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=1&toGame=82")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(5000));
    }

    @Test
    @DisplayName("and so does its goalie leaderboard")
    void premium_getsTheGoalies() throws Exception {
        withPremium();
        withOneSkaterAndOneGoalie();

        mockMvc.perform(get("/api/v1/projection-model/splits/goalies?fromGame=1&toGame=82")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * A subscription that has lapsed is a row in the table with premium false, not a missing
     * row, so the two have to answer the same way.
     */
    @Test
    @DisplayName("a lapsed subscription is refused like no subscription at all")
    void lapsedSubscription_isForbidden() throws Exception {
        when(databaseServiceClient.getSubscription(USER_ID)).thenReturn(Optional.of(
                new SubscriptionResponse()
                        .status(SubscriptionResponse.StatusEnum.CANCELED)
                        .premium(false)
                        .cancelAtPeriodEnd(true)
                        .provider("mock")));

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=1&toGame=82")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
