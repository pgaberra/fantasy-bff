package com.fantasy.bff.controller;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.request.EspnCredentialsRequest;
import com.fantasy.bff.dto.response.EspnLeagueTeamsResponse;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.generated.espn.model.CredentialStatusResponse;
import com.fantasy.bff.generated.espn.model.CredentialValuesResponse;
import com.fantasy.bff.service.EspnLeagueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated user's ESPN fantasy integration. ESPN has no OAuth, so instead of a
 * connect flow the user supplies a league id and, for private leagues, their
 * espn_s2 + SWID cookies. The app user id (JWT subject) is forwarded to fantasy-espn-service,
 * which stores the cookies and reads the league.
 */
@Tag(name = "ESPN", description = "The authenticated user's ESPN fantasy leagues")
@RestController
@RequestMapping("/api/v1/espn")
public class EspnController {

    private final EspnServiceClient espnServiceClient;
    private final EspnLeagueService espnLeagueService;

    public EspnController(EspnServiceClient espnServiceClient, EspnLeagueService espnLeagueService) {
        this.espnServiceClient = espnServiceClient;
        this.espnLeagueService = espnLeagueService;
    }

    @Operation(summary = "Whether the user has stored ESPN cookies (for private leagues)")
    @ApiResponse(responseCode = "200", description = "Status returned")
    @GetMapping("/credentials")
    public CredentialStatusResponse credentialStatus(@AuthenticationPrincipal String userId) {
        return espnServiceClient.credentialStatus(userId);
    }

    @Operation(summary = "The caller's own stored ESPN cookies",
            description = "Returns the stored espn_s2 and SWID so the import form can show the "
                    + "user their saved connection. Scoped to the JWT subject — a caller can only "
                    + "ever read their own. 404 when they have stored none.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cookies returned"),
            @ApiResponse(responseCode = "404", description = "Caller has no stored cookies")
    })
    @GetMapping("/credentials/values")
    public CredentialValuesResponse credentialValues(@AuthenticationPrincipal String userId) {
        return espnServiceClient.credentialValues(userId);
    }

    @Operation(summary = "Store (or replace) the user's ESPN cookies")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cookies stored"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid cookie values")
    })
    @PutMapping("/credentials")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveCredentials(@AuthenticationPrincipal String userId,
                                @Valid @RequestBody EspnCredentialsRequest request) {
        espnServiceClient.saveCredentials(userId, request.espnS2(), request.swid());
    }

    @Operation(summary = "Delete the user's stored ESPN cookies")
    @ApiResponse(responseCode = "204", description = "Cookies deleted (or none existed)")
    @DeleteMapping("/credentials")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCredentials(@AuthenticationPrincipal String userId) {
        espnServiceClient.deleteCredentials(userId);
    }

    @Operation(summary = "Get an ESPN league's settings mapped to projection settings",
            description = "Maps the ESPN league's scoring basis (points vs category), active stat "
                    + "columns, point weights, roster slots and size into the projection's own shape.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mapped settings returned"),
            @ApiResponse(responseCode = "400", description = "League is private (cookies missing/invalid) "
                    + "or the league id is malformed"),
            @ApiResponse(responseCode = "404", description = "No such ESPN league for that id in the current or previous season")
    })
    @GetMapping("/leagues/{leagueId}/projection-settings")
    public LeagueProjectionSettingsResponse projectionSettings(@AuthenticationPrincipal String userId,
                                                               @PathVariable String leagueId) {
        return espnLeagueService.projectionSettings(userId, leagueId);
    }

    @Operation(summary = "List an ESPN league's teams (names + which is the user's own)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Teams returned"),
            @ApiResponse(responseCode = "400", description = "League is private (cookies missing/invalid) "
                    + "or the league id is malformed"),
            @ApiResponse(responseCode = "404", description = "No such ESPN league for that id in the current or previous season")
    })
    @GetMapping("/leagues/{leagueId}/teams")
    public EspnLeagueTeamsResponse teams(@AuthenticationPrincipal String userId,
                                         @PathVariable String leagueId) {
        return espnLeagueService.teams(userId, leagueId);
    }
}
