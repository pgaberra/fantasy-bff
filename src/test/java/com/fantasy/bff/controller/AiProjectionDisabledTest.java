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
 * An environment with AI_PROJECTION_ENABLED=false must refuse the model's own lines rather than
 * merely have the web hide the preset: a request that skips the UI would otherwise still be served.
 *
 * <p>The prefix is left readable here (security.projection-model-enabled=true) on purpose —
 * otherwise the 403 from that switch would hide whether this one does anything at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ai-projection.enabled=false",
        "security.projection-model-enabled=true",
        "services.projection.player-mapping-ttl-ms=0"
})
class AiProjectionDisabledTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private ProjectionServiceClient projectionServiceClient;
    @MockitoBean private PlayerServiceClient playerServiceClient;
    /** Keeps the ESPN stat-line enrichment off the network; unstubbed means "no ESPN stats". */
    @MockitoBean private EspnServiceClient espnServiceClient;

    private String token;

    @BeforeEach
    void setUp() {
        token = jwtTokenValidator.generateToken("user-1", "test@example.com");
    }

    @Test
    @DisplayName("the model's lines are not served at all")
    void seed_isNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/seed").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("and the environment reports the AI projection as unavailable")
    void features_reportItUnavailable() throws Exception {
        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiProjection").value(false));
    }

    @Test
    @DisplayName("and the projection service is never asked for them")
    void seed_neverReachesTheProjectionService() throws Exception {
        mockMvc.perform(get("/api/v1/projection-model/seed").header("Authorization", "Bearer " + token));

        verifyNoInteractions(projectionServiceClient);
    }

    /**
     * The splits share the path prefix but not the feature: they are what players actually did,
     * which Who's hot reads and which has a switch of its own in the web. Switching the model
     * off must not take that page down with it.
     */
    @Test
    @DisplayName("the measured game-range splits behind Who's hot are untouched")
    void splits_areStillServed() throws Exception {
        SkaterSplitResponse split = new SkaterSplitResponse();
        split.setNhlId(8478402);
        split.setSeason(2025);
        split.setGames(20);
        split.setPoints(33);
        when(projectionServiceClient.skaterSplits(any(), any(GameRange.class), anyInt()))
                .thenReturn(List.of(split));
        when(playerServiceClient.getSkaters(nullable(Integer.class)))
                .thenReturn(List.of(new SkaterResponse(5000, "Connor McDavid", "EDM", null, 97, Set.of(), null)));
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());
        // The split arrives keyed by NHL id; naming the player is what maps it onto ours.
        PlayerResponse mcdavid = new PlayerResponse();
        mcdavid.setNhlId(8478402);
        mcdavid.setFullName("Connor McDavid");
        mcdavid.setCurrentTeam("EDM");
        mcdavid.setSweaterNumber(97);
        mcdavid.setIsActive(true);
        when(projectionServiceClient.activePlayers(any())).thenReturn(List.of(mcdavid));

        mockMvc.perform(get("/api/v1/projection-model/splits/skaters?lastGames=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(5000));
    }
}
