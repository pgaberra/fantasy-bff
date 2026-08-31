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
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.SkaterSplitResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import java.util.List;
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
 * Where premium is not sold, nothing is held back for it. {@code payments.enabled} is off here,
 * which is the default and the state of every environment deployed so far: there is no checkout
 * to send anyone to, and the web's pricing page redirects home. Gating the range then would be a
 * door with no handle rather than an upsell.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "payments.enabled=false",
        "security.projection-model-enabled=true",
        "services.projection.player-mapping-ttl-ms=0"
})
class PremiumGameRangeUnsoldTest extends BaseIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

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
        SkaterSplitResponse skater = new SkaterSplitResponse();
        skater.setNhlId(8478402);
        skater.setSeason(2025);
        skater.setGames(82);
        skater.setPoints(153);
        when(projectionServiceClient.skaterSplits(anyInt(), any(GameRange.class), anyInt()))
                .thenReturn(List.of(skater));
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
    @DisplayName("any stretch of the season is served, subscription or not")
    void wholeSeason_isServed() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=1&toGame=82")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(5000));
    }

    /**
     * The flag is read before the subscription is, so an environment that sells nothing never
     * asks db-service whether anyone has bought anything.
     */
    @Test
    @DisplayName("and no subscription is ever looked up to decide it")
    void wholeSeason_doesNotReadTheSubscription() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?fromGame=1&toGame=82")
                .header("Authorization", "Bearer " + token));

        verifyNoInteractions(databaseServiceClient);
    }
}
