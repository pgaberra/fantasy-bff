package com.fantasy.bff.controller;

import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.AuthorizeUrlResponse;
import com.fantasy.bff.generated.yahoo.model.ConnectionResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueTeamsResponse;
import com.fantasy.bff.generated.yahoo.model.LeaguesResponse;
import com.fantasy.bff.service.YahooLeagueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated user's Yahoo fantasy integration. The app user id (JWT subject) is
 * forwarded to fantasy-yahoo-service, which owns the OAuth tokens and league data.
 */
@Tag(name = "Yahoo", description = "The authenticated user's Yahoo fantasy account and leagues")
@RestController
@RequestMapping("/api/v1/yahoo")
public class YahooController {

    private final YahooServiceClient yahooServiceClient;
    private final YahooLeagueService yahooLeagueService;

    public YahooController(YahooServiceClient yahooServiceClient, YahooLeagueService yahooLeagueService) {
        this.yahooServiceClient = yahooServiceClient;
        this.yahooLeagueService = yahooLeagueService;
    }

    @Operation(summary = "Start connecting the user's Yahoo account",
            description = "Returns the Yahoo consent URL the web app should redirect the browser to.")
    @ApiResponse(responseCode = "200", description = "Authorization URL created")
    @PostMapping("/connect")
    public AuthorizeUrlResponse connect(@AuthenticationPrincipal String userId) {
        return yahooServiceClient.authorizeUrl(userId);
    }

    @Operation(summary = "Whether the user has connected their Yahoo account")
    @ApiResponse(responseCode = "200", description = "Connection status returned")
    @GetMapping("/connection")
    public ConnectionResponse connection(@AuthenticationPrincipal String userId) {
        return yahooServiceClient.connection(userId);
    }

    @Operation(summary = "List the user's NHL fantasy leagues")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leagues returned"),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo")
    })
    @GetMapping("/leagues")
    public LeaguesResponse leagues(@AuthenticationPrincipal String userId) {
        return yahooServiceClient.leagues(userId);
    }

    @Operation(summary = "Get a league's settings mapped to projection settings",
            description = "Maps the Yahoo league's scoring basis (points vs category), active stat "
                    + "columns, point weights, roster slots and size into the projection's own shape.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mapped settings returned"),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo")
    })
    @GetMapping("/leagues/{leagueKey}/projection-settings")
    public LeagueProjectionSettingsResponse projectionSettings(@AuthenticationPrincipal String userId,
                                                               @PathVariable String leagueKey) {
        return yahooLeagueService.projectionSettings(userId, leagueKey);
    }

    @Operation(summary = "List a league's teams (names + which is the user's own)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Teams returned"),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo")
    })
    @GetMapping("/leagues/{leagueKey}/teams")
    public LeagueTeamsResponse teams(@AuthenticationPrincipal String userId,
                                     @PathVariable String leagueKey) {
        return yahooServiceClient.teams(userId, leagueKey);
    }
}
