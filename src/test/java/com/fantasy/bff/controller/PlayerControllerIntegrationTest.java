package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private PlayerServiceClient playerServiceClient;

    /**
     * Player responses are enriched with ESPN's stat lines. Mocking the client keeps the test
     * off the network — an unstubbed call returns null, which the provider reads as "no ESPN
     * stats" and serves the Yahoo line unchanged.
     */
    @MockitoBean
    private EspnServiceClient espnServiceClient;

    @Test
    void getSkaters_withValidToken_returns200() throws Exception {
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of(
                new SkaterResponse(1, "Connor McDavid", "EDM",
                        "https://assets.nhle.com/mugs/nhl/20242025/EDM/8478402.png", 97, Set.of(SkaterPosition.C),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(82, 1320),
                                new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1, 23, 38, 61, 8, 2,
                                        348, 18.4, 812, 623, 42, 28, 0, 1408, 92400)
                        ))
        ));

        String token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        mockMvc.perform(get("/api/v1/players/skaters")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Connor McDavid"))
                .andExpect(jsonPath("$[0].teamAbbrev").value("EDM"))
                .andExpect(jsonPath("$[0].headshot").value("https://assets.nhle.com/mugs/nhl/20242025/EDM/8478402.png"))
                .andExpect(jsonPath("$[0].positions[0]").value("C"));
    }

    @Test
    void getSkaters_withoutToken_returns200() throws Exception {
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/players/skaters"))
                .andExpect(status().isOk());
    }

    @Test
    void getSkaters_withInvalidToken_returns200() throws Exception {
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/players/skaters")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isOk());
    }

    @Test
    void getGoalies_withValidToken_returns200() throws Exception {
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of(
                new GoalieResponse(101, "Igor Shesterkin", "NYR",
                        "https://assets.nhle.com/mugs/nhl/20242025/NYR/8478048.png", 31,
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(58),
                                new GoalieResponse.ScoringStats(58, 36, 17, 4, 3, 1720, 1565, 155, 2.67, 0.910, 0.632, 209000)
                        ))
        ));

        String token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        mockMvc.perform(get("/api/v1/players/goalies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Igor Shesterkin"));
    }

    @Test
    void getGoalies_withoutToken_returns200() throws Exception {
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/players/goalies"))
                .andExpect(status().isOk());
    }

    // A preview wants the top of the board, not the pool — the whole point of the parameter is
    // that the browser stops downloading half a megabyte to draw five rows.
    @Test
    void getSkaters_withALimit_returnsThatMany() throws Exception {
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of(
                skater(1, "First", 120), skater(2, "Second", 90), skater(3, "Third", 60)));

        mockMvc.perform(get("/api/v1/players/skaters").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("First"));
    }

    @Test
    void getSkaters_withALimitOfZero_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/players/skaters").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSkaters_withALimitOverTheCap_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/players/skaters").param("limit", "501"))
                .andExpect(status().isBadRequest());
    }

    private static SkaterResponse skater(int id, String name, int points) {
        return new SkaterResponse(id, name, "EDM", "/players/" + id + "/headshot", 97,
                Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(82, 1320),
                        new SkaterResponse.ScoringStats(0, 0, points, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0.0, 0, 0, 0, 0, 0, 0, 0)));
    }

    @Test
    void getSkaters_whenServiceFails_returns502() throws Exception {
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenThrow(new RuntimeException("player service down"));

        String token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        mockMvc.perform(get("/api/v1/players/skaters")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DOWNSTREAM_UNAVAILABLE"));
    }

    /**
     * The pool is refreshed about once a day, so re-downloading it on every page load is waste.
     * Asserted through the real filter chain because Spring Security writes its own
     * {@code no-store} on everything that has not set a Cache-Control of its own — this passing
     * is what says the controller's header survives to the client.
     */
    @Test
    void getSkaters_letsTheBrowserHoldOntoThePoolBriefly() throws Exception {
        when(playerServiceClient.getSkaters(nullable(Integer.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/players/skaters"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", not(containsString("no-store"))))
                .andExpect(header().doesNotExist("Pragma"));
    }

    @Test
    void getGoalies_letsTheBrowserHoldOntoThePoolBriefly() throws Exception {
        when(playerServiceClient.getGoalies(nullable(Integer.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/players/goalies"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(header().string("Cache-Control", not(containsString("no-store"))));
    }

    /** Everything that is not deliberately public keeps Spring Security's no-store posture. */
    @Test
    void aResponseThatDoesNotOptInIsStillNeverStored() throws Exception {
        String token = jwtTokenValidator.generateToken("user-1", "test@example.com");

        mockMvc.perform(get("/api/v1/players/rookies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    /**
     * An {@code <img>} sends no Authorization header, so a headshot that needed a token would
     * render as a broken image for everyone.
     */
    @Test
    void getHeadshot_withoutToken_returnsThePngAndTellsTheBrowserToKeepIt() throws Exception {
        byte[] thumbnail = {(byte) 0x89, 'P', 'N', 'G'};
        when(playerServiceClient.getHeadshot(1)).thenReturn(Optional.of(thumbnail));

        mockMvc.perform(get("/api/v1/players/1/headshot"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(thumbnail))
                .andExpect(header().string("Cache-Control", containsString("max-age=604800")))
                .andExpect(header().exists("ETag"));
    }

    @Test
    void getHeadshot_returns404WhenThePlayerHasNone() throws Exception {
        when(playerServiceClient.getHeadshot(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/players/1/headshot"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHeadshot_whenServiceFails_returns502() throws Exception {
        when(playerServiceClient.getHeadshot(1)).thenThrow(new RuntimeException("player service down"));

        mockMvc.perform(get("/api/v1/players/1/headshot"))
                .andExpect(status().isBadGateway());
    }
}
