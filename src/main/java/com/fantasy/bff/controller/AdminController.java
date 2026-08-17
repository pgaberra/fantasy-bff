package com.fantasy.bff.controller;

import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.generated.yahoo.model.SyncAcceptedResponse;
import com.fantasy.bff.generated.yahoo.model.SyncRunResponse;
import com.fantasy.bff.generated.yahoo.model.YahooProbeResponse;
import com.fantasy.bff.generated.yahoo.model.AuthorizeUrlResponse;
import com.fantasy.bff.generated.yahoo.model.ConnectionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only operations (gated by ROLE_ADMIN in SecurityConfig). Manages the app-owned
 * Yahoo "service account" used for the league-wide player-position fetch, and triggers a
 * player-service sync. The service account is keyed under a reserved app-user id so it
 * never collides with a real user's Yahoo connection.
 */
@Tag(name = "Admin", description = "Admin-only: Yahoo service account + player sync")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final String SERVICE_ACCOUNT_ID = "__service__";

    private final YahooServiceClient yahooServiceClient;
    private final PlayerServiceClient playerServiceClient;

    public AdminController(YahooServiceClient yahooServiceClient, PlayerServiceClient playerServiceClient) {
        this.yahooServiceClient = yahooServiceClient;
        this.playerServiceClient = playerServiceClient;
    }

    @Operation(summary = "Start connecting the Yahoo service account",
            description = "Returns the Yahoo consent URL to send the admin's browser to. The "
                    + "resulting token is stored under the reserved service-account id.")
    @ApiResponse(responseCode = "200", description = "Authorization URL created")
    @PostMapping("/yahoo/connect")
    public AuthorizeUrlResponse connectYahooServiceAccount() {
        return yahooServiceClient.authorizeUrl(SERVICE_ACCOUNT_ID);
    }

    @Operation(summary = "Whether the Yahoo service account is connected")
    @ApiResponse(responseCode = "200", description = "Connection status returned")
    @GetMapping("/yahoo/connection")
    public ConnectionResponse yahooServiceConnection() {
        return yahooServiceClient.connection(SERVICE_ACCOUNT_ID);
    }

    @Operation(summary = "Trigger a player sync",
            description = "Refreshes the cached player read model from Yahoo.")
    @ApiResponse(responseCode = "200", description = "Sync triggered")
    @PostMapping("/player/sync")
    public SyncAcceptedResponse triggerPlayerSync() {
        return playerServiceClient.triggerSync();
    }

    @Operation(summary = "Recent player sync runs",
            description = "Outcome (counts) and the diff (players added/removed) of the most recent syncs.")
    @ApiResponse(responseCode = "200", description = "Runs returned")
    @GetMapping("/player/sync/runs")
    public List<SyncRunResponse> playerSyncRuns(@RequestParam(defaultValue = "10") int limit) {
        return playerServiceClient.getSyncRuns(limit);
    }

    @Operation(summary = "Ask Yahoo whether it will serve a game's players",
            description = "One live call, reporting the status Yahoo answered with and its own "
                    + "error wording. A failing sync only says that something was refused; vary "
                    + "the game key and season here to find out what. Reads nothing into the "
                    + "cache and writes nothing.")
    @ApiResponse(responseCode = "200", description = "What Yahoo answered, refusal included")
    @GetMapping("/yahoo/probe")
    public YahooProbeResponse probeYahooAccess(
            @Parameter(description = "Yahoo game: \"nhl\" for the current season, or a numeric key to pin a past one")
            @RequestParam(defaultValue = "nhl") String gameKey,
            @Parameter(description = "Season start year; omit to send no season filter at all")
            @RequestParam(required = false) String season) {
        return playerServiceClient.probeYahooAccess(gameKey, season);
    }
}
