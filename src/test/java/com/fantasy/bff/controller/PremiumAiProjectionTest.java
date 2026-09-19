package com.fantasy.bff.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.PremiumEntitlementResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import java.math.BigDecimal;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The AI projection is what premium pays for. The web keeps it visible to a free account and
 * sells it rather than hiding it, so every way to the model's actual lines has to be refused
 * here — a visible starting point that the server let through would be the whole feature given
 * away by anyone who read the network tab. The exception is the top of the board, small enough
 * to be the preview that page shows a free account and priced in as the teaser it is.
 *
 * <p>There are two such ways, and they do not share a code path: {@code /projection-model/seed}
 * hands the lines to the new-projection page, and {@code source=model} has the server fill a
 * projection (or a preset draft) with them without the client ever seeing them.
 *
 * <p>Payments are switched on throughout, since that is the only state in which anything is
 * held back. {@link PremiumAiProjectionUnsoldTest} covers the other one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "payments.enabled=true",
            "payments.provider=mock",
            "payments.mock.webhook-secret=test-mock-secret",
            "security.projection-model-enabled=true",
            "services.projection.player-mapping-ttl-ms=0"
        })
class PremiumAiProjectionTest extends BaseIntegrationTest {

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
        when(databaseServiceClient.getPremiumEntitlement(USER_ID)).thenReturn(
                new PremiumEntitlementResponse()
                        .premium(false)
                        .source(PremiumEntitlementResponse.SourceEnum.NONE)
                        .cancelAtPeriodEnd(false));
    }

    private void withPremium() {
        when(databaseServiceClient.getPremiumEntitlement(USER_ID)).thenReturn(
                new PremiumEntitlementResponse()
                        .premium(true)
                        .source(PremiumEntitlementResponse.SourceEnum.SUBSCRIPTION)
                        .subscriptionStatus(PremiumEntitlementResponse.SubscriptionStatusEnum.ACTIVE)
                        .cancelAtPeriodEnd(false)
                        .currentPeriodEnd(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))
                        .premiumUntil(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30)));
    }

    private void withGrantedPremium() {
        when(databaseServiceClient.getPremiumEntitlement(USER_ID)).thenReturn(
                new PremiumEntitlementResponse()
                        .premium(true)
                        .source(PremiumEntitlementResponse.SourceEnum.GRANT)
                        .cancelAtPeriodEnd(false)
                        .grantExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(2))
                        .premiumUntil(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(2)));
    }

    /** One projected skater the platform also carries, so a seed has something to return. */
    private void withOneProjectedSkater() {
        PlayerResponse mcdavid = new PlayerResponse();
        mcdavid.setNhlId(8478402);
        mcdavid.setFullName("Connor McDavid");
        mcdavid.setCurrentTeam("EDM");
        mcdavid.setSweaterNumber(97);
        mcdavid.setIsActive(true);
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(mcdavid));

        SkaterProjectionResponse projected = new SkaterProjectionResponse();
        projected.setNhlId(8478402);
        projected.setPoints(BigDecimal.valueOf(140));
        projected.setGoals(BigDecimal.valueOf(60));
        when(projectionServiceClient.skaterProjections(anyInt(), any()))
                .thenReturn(List.of(projected));
        when(projectionServiceClient.goalieProjections(anyInt(), any()))
                .thenReturn(List.of());

        when(playerServiceClient.getSkaters(nullable(Integer.class)))
                .thenReturn(
                        List.of(
                                new SkaterResponse(
                                        5000, "Connor McDavid", "EDM", null, 97, Set.of(), null)));
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
    }

    /**
     * The settings a projection is created with, without the player rows: `source` is what has
     * the server fill those in, which is the whole point of the two starting points.
     */
    private static String bodyWith(String extra) {
        return """
                {
                  "name": "My board",
                  %s
                  "data": {
                    "settings": {
                      "scoringType": "points",
                      "statWeights": { "goals": 4.5 },
                      "activeScoringColumns": ["goals"],
                      "activeUtilityColumns": ["gp"],
                      "scaleSettings": {},
                      "decimalSettings": { "goals": 0 },
                      "useDefaultDecimals": true
                    }
                  }
                }
                """
                .formatted(extra);
    }

    private static String fromModel() {
        return bodyWith("\"source\": \"model\",");
    }

    private static String fromLastSeason() {
        return bodyWith("\"source\": \"default\",");
    }

    private static String presetDraftFromModel() {
        return bodyWith("\"source\": \"model\", \"kind\": \"draft\",");
    }

    @Test
    @DisplayName("the model's lines are refused to an account without premium")
    void seed_withoutPremium_isForbidden() throws Exception {
        withoutASubscription();

        mockMvc.perform(get("/api/v1/projection-model/seed").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PREMIUM_REQUIRED"));
    }

    /**
     * The few rows the new-projection page draws as a preview are the teaser, and deliberately
     * not behind the gate: a starting point nobody may look at is a hard thing to want.
     */
    @Test
    @DisplayName("the top of the board is served to an account without premium")
    void seedWithAPreviewLimit_withoutPremium_isServed() throws Exception {
        withoutASubscription();
        withOneProjectedSkater();

        mockMvc.perform(
                        get("/api/v1/projection-model/seed?skaterLimit=25&goalieLimit=10")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].playerId").value(5000));
    }

    /** One row past the preview is the board being read a page at a time. */
    @Test
    @DisplayName("but asking for more of it than the preview is refused")
    void seedWithATooWideLimit_withoutPremium_isForbidden() throws Exception {
        withoutASubscription();

        mockMvc.perform(
                        get("/api/v1/projection-model/seed?skaterLimit=26&goalieLimit=10")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PREMIUM_REQUIRED"));
    }

    /** An absent limit means every one of them, however small the other limit is. */
    @Test
    @DisplayName("and so is a preview-sized limit on only one of the two lists")
    void seedWithOneLimit_withoutPremium_isForbidden() throws Exception {
        withoutASubscription();

        mockMvc.perform(
                        get("/api/v1/projection-model/seed?skaterLimit=5")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PREMIUM_REQUIRED"));
    }

    /** The refusal lands before the model is asked, so a board nobody may read is never built. */
    @Test
    @DisplayName("the refusal never reaches the projection service")
    void seed_withoutPremium_buildsNothing() throws Exception {
        withoutASubscription();

        mockMvc.perform(get("/api/v1/projection-model/seed").header("Authorization", "Bearer " + token));

        verifyNoInteractions(projectionServiceClient);
    }

    /**
     * Seeding a projection server-side never shows the client a line, but it puts the whole
     * board in their account, which is the same thing bought a different way.
     */
    @Test
    @DisplayName("creating a projection from the model is refused as well as reading it")
    void createFromModel_withoutPremium_isForbidden() throws Exception {
        withoutASubscription();

        mockMvc.perform(
                        post("/api/v1/projections")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fromModel()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PREMIUM_REQUIRED"));

        verify(databaseServiceClient, never()).createProjection(eq(USER_ID), any());
    }

    /** A preset draft is the other door to a server-seeded model board. */
    @Test
    @DisplayName("and so is starting a draft against it")
    void presetDraftFromModel_withoutPremium_isForbidden() throws Exception {
        withoutASubscription();

        mockMvc.perform(
                        post("/api/v1/projections")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(presetDraftFromModel()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PREMIUM_REQUIRED"));
    }

    /**
     * Last season's stats are the free starting point. Gating the model must not catch it, or a
     * free account is left unable to create a projection at all.
     */
    @Test
    @DisplayName("the free starting point still creates a projection")
    void createFromLastSeason_withoutPremium_isServed() throws Exception {
        withoutASubscription();
        // Real stats, unlike the identity-only rows the model path needs: this is the source
        // that copies last season's numbers, so there have to be some to copy.
        when(playerServiceClient.getSkaters(nullable(Integer.class)))
                .thenReturn(
                        List.of(
                                new SkaterResponse(
                                        5000,
                                        "Connor McDavid",
                                        "EDM",
                                        null,
                                        97,
                                        Set.of(SkaterPosition.C),
                                        new SkaterResponse.Stats(
                                                new SkaterResponse.UtilityStats(82, 1320),
                                                new SkaterResponse.ScoringStats(
                                                        64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1, 23,
                                                        38, 61, 8, 2, 348, 18.4, 812, 623, 42, 28, 0,
                                                        1408, 92400)))));
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        when(databaseServiceClient.createProjection(eq(USER_ID), any()))
                .thenReturn(new ProjectionResponse()
                        .id(UUID.randomUUID().toString())
                        .name("My board")
                        .season(ProjectionResponse.SeasonEnum._20262027));

        mockMvc.perform(
                        post("/api/v1/projections")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fromLastSeason()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a premium account gets the model's lines")
    void seed_withPremium_isServed() throws Exception {
        withPremium();
        withOneProjectedSkater();

        mockMvc.perform(get("/api/v1/projection-model/seed").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].playerId").value(5000));
    }

    @Test
    @DisplayName("so does an account that was given premium rather than sold it")
    void seed_withGrantedPremium_isServed() throws Exception {
        withGrantedPremium();
        withOneProjectedSkater();

        mockMvc.perform(get("/api/v1/projection-model/seed").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].playerId").value(5000));
    }

    @Test
    @DisplayName("and can create a projection from them")
    void createFromModel_withPremium_isServed() throws Exception {
        withPremium();
        withOneProjectedSkater();
        when(databaseServiceClient.createProjection(eq(USER_ID), any()))
                .thenReturn(new ProjectionResponse()
                        .id(UUID.randomUUID().toString())
                        .name("AI Projection")
                        .season(ProjectionResponse.SeasonEnum._20262027));

        mockMvc.perform(
                        post("/api/v1/projections")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fromModel()))
                .andExpect(status().isCreated());
    }

    /**
     * A lapsed subscription is a row with premium false rather than a missing row, so the two
     * have to answer the same way.
     */
    @Test
    @DisplayName("a lapsed subscription is refused like no subscription at all")
    void lapsedSubscription_isForbidden() throws Exception {
        when(databaseServiceClient.getPremiumEntitlement(USER_ID)).thenReturn(
                new PremiumEntitlementResponse()
                        .premium(false)
                        .source(PremiumEntitlementResponse.SourceEnum.NONE)
                        .subscriptionStatus(PremiumEntitlementResponse.SubscriptionStatusEnum.CANCELED)
                        .cancelAtPeriodEnd(true));

        mockMvc.perform(get("/api/v1/projection-model/seed").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
