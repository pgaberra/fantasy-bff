package com.fantasy.bff.controller;

import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.request.YahooLinkClaimRequest;
import com.fantasy.bff.dto.response.ErrorDto;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.AuthorizeUrlResponse;
import com.fantasy.bff.generated.yahoo.model.ConnectionResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueTeamsResponse;
import com.fantasy.bff.generated.yahoo.model.LeaguesResponse;
import com.fantasy.bff.service.YahooLeagueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    static final String REFUSED = "Yahoo refused the request (code YAHOO_ACCESS_DENIED); "
            + "the message carries Yahoo's own wording";

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

    @Operation(summary = "Finish connecting the user's Yahoo account",
            description = "Attaches the Yahoo authorization from the consent the browser just returned "
                    + "from, if this user is the one who started it. "
                    + "The code is single-use and expires after five minutes. 404 means it is unknown, used or "
                    + "expired; 409 means a different account started this connection, and the Yahoo "
                    + "authorization has been discarded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Yahoo account connected"),
            @ApiResponse(responseCode = "400", description = "Malformed code",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "404", description = "Code unknown, already used or expired",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "409", description = "Another account started this connection; discarded",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @PostMapping("/connect/complete")
    public ConnectionResponse completeConnect(@AuthenticationPrincipal String userId,
                                              @Valid @RequestBody YahooLinkClaimRequest request) {
        return yahooServiceClient.completeLink(userId, request.code());
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
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo"),
            @ApiResponse(responseCode = "424", description = REFUSED,
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
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
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo"),
            @ApiResponse(responseCode = "424", description = REFUSED,
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/leagues/{leagueKey}/projection-settings")
    public LeagueProjectionSettingsResponse projectionSettings(@AuthenticationPrincipal String userId,
                                                               @PathVariable String leagueKey) {
        return yahooLeagueService.projectionSettings(userId, leagueKey);
    }

    @Operation(summary = "List a league's teams (names + which is the user's own)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Teams returned"),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo"),
            @ApiResponse(responseCode = "424", description = REFUSED,
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/leagues/{leagueKey}/teams")
    public LeagueTeamsResponse teams(@AuthenticationPrincipal String userId,
                                     @PathVariable String leagueKey) {
        return yahooServiceClient.teams(userId, leagueKey);
    }
}
