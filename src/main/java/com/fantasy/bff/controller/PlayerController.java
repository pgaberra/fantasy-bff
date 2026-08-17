package com.fantasy.bff.controller;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.RookiesResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.service.PlayerService;
import com.fantasy.bff.service.RookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "Players", description = "NHL player stats endpoints (public, read-only)")
public class PlayerController {

    private static final Duration HEADSHOT_MAX_AGE = Duration.ofDays(7);

    /**
     * The player pool is a read model yahoo-service refreshes about once a day, so re-fetching it
     * on every page load is wasted work. A minute is short enough that nobody meets a pool that
     * has visibly moved on, and long enough to cover opening several projections in a row.
     *
     * <p>Cached publicly rather than privately because the endpoint is public: the same bytes go
     * to every caller, signed in or not. Set here rather than left to Spring Security, whose
     * default {@code no-store} is the right posture for the rest of the API and stops even a
     * revalidation from being possible.
     */
    private static final Duration PLAYER_POOL_MAX_AGE = Duration.ofSeconds(60);

    private final PlayerService playerService;
    private final RookieService rookieService;

    public PlayerController(PlayerService playerService, RookieService rookieService) {
        this.playerService = playerService;
        this.rookieService = rookieService;
    }

    @GetMapping("/skaters")
    @Operation(summary = "Get all skaters", description = "Returns all skaters with full season stats")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Skaters retrieved successfully"),
            @ApiResponse(responseCode = "502", description = "NHL service unavailable")
    })
    public ResponseEntity<List<SkaterResponse>> getSkaters() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(PLAYER_POOL_MAX_AGE).cachePublic())
                .body(playerService.getSkaters());
    }

    @GetMapping("/goalies")
    @Operation(summary = "Get all goalies", description = "Returns all goalies with full season stats")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goalies retrieved successfully"),
            @ApiResponse(responseCode = "502", description = "NHL service unavailable")
    })
    public ResponseEntity<List<GoalieResponse>> getGoalies() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(PLAYER_POOL_MAX_AGE).cachePublic())
                .body(playerService.getGoalies());
    }

    @GetMapping("/rookies")
    @Operation(summary = "Which players are rookies",
            description = "Player ids of the rookies for the season being projected, by the "
                    + "NHL's rule: under 26 on September 15, no earlier season of 25+ games, and "
                    + "no two earlier seasons of more than 6. Answers known=false when rookie "
                    + "status cannot be determined, which is not the same as there being none.")
    @ApiResponse(responseCode = "200", description = "Rookies returned, or reported as unknown")
    public ResponseEntity<RookiesResponse> getRookies() {
        return ResponseEntity.ok(rookieService.rookies());
    }

    @GetMapping(value = "/{playerId}/headshot", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Get a player's headshot",
            description = "The thumbnail yahoo-service renders from the player's headshot. This is "
                    + "what the `headshot` path on a skater or goalie points at; the source images "
                    + "themselves are multi-megapixel originals and are never served to a browser.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Headshot returned"),
            @ApiResponse(responseCode = "404", description = "No headshot for this player"),
            @ApiResponse(responseCode = "502", description = "Player service unavailable")
    })
    public ResponseEntity<byte[]> getHeadshot(@PathVariable int playerId) {
        return playerService.getHeadshot(playerId)
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(HEADSHOT_MAX_AGE).cachePublic())
                        .eTag(Integer.toHexString(Arrays.hashCode(image)))
                        .body(image))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
