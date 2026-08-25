package com.fantasy.bff.controller;

import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.PlayerIdRemapReport;
import com.fantasy.bff.service.PlayerIdRemapService;
import com.fantasy.bff.generated.yahoo.model.SyncAcceptedResponse;
import com.fantasy.bff.generated.yahoo.model.SyncRunResponse;
import com.fantasy.bff.generated.yahoo.model.YahooProbeResponse;
import com.fantasy.bff.generated.yahoo.model.AuthorizeUrlResponse;
import com.fantasy.bff.generated.yahoo.model.ConnectionResponse;
import com.fantasy.bff.generated.yahoo.model.LeaguesResponse;
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
    private final PlayerIdRemapService playerIdRemapService;

    public AdminController(YahooServiceClient yahooServiceClient,
                           PlayerServiceClient playerServiceClient,
                           PlayerIdRemapService playerIdRemapService) {
        this.playerIdRemapService = playerIdRemapService;
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
            @RequestParam(required = false) String season,
            @Parameter(description = "Ask this league's players instead of the whole game's. The "
                    + "granted Yahoo scope is about leagues, so this may be served where a game "
                    + "is refused — and that difference is the diagnosis.")
            @RequestParam(required = false) String leagueKey,
            @Parameter(description = "Set to \"leagues\" to ask the floor question instead: can "
                    + "this account list its own leagues at all? Everything else is ignored. A "
                    + "refusal here means no route into the Fantasy API is open, not that the "
                    + "wrong one was chosen.")
            @RequestParam(required = false) String target) {
        return playerServiceClient.probeYahooAccess(gameKey, season, leagueKey, target);
    }

    @Operation(summary = "The Yahoo service account's own leagues",
            description = "Hands you a league key for the probe without hunting for one, and is "
                    + "itself a test: succeeding here while a game probe is refused places the "
                    + "refusal on what was asked for rather than on who asked.")
    @ApiResponse(responseCode = "200", description = "Leagues returned")
    @GetMapping("/yahoo/leagues")
    public LeaguesResponse yahooServiceAccountLeagues() {
        return yahooServiceClient.leagues(SERVICE_ACCOUNT_ID);
    }

    @Operation(summary = "Remap saved player ids from Yahoo's numbering to ESPN's",
            description = "Matches the Yahoo player pool to the ESPN one and rewrites the ids in "
                    + "every saved projection, draft pick and share. Reports the match before the "
                    + "write, and defaults to a dry run: pass dryRun=false to actually apply it. "
                    + "A one-way move — the app should be serving the ESPN pool afterwards.")
    @ApiResponse(responseCode = "200", description = "Match reported, and applied unless it was a dry run")
    @ApiResponse(responseCode = "502", description = "Either pool came back short, or too little of the Yahoo pool matched to apply")
    @PostMapping("/player-ids/remap")
    public PlayerIdRemapReport remapPlayerIds(
            @Parameter(description = "Write nothing and report what would change. Defaults to true.")
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return playerIdRemapService.remap(dryRun);
    }
}
