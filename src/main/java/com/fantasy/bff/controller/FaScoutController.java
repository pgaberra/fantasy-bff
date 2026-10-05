package com.fantasy.bff.controller;

import com.fantasy.bff.dto.response.ScoutListResponse;
import com.fantasy.bff.dto.response.ScoutMyTeamResponse;
import com.fantasy.bff.service.FaScoutService;
import com.fantasy.bff.service.PlayerIdSpace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The FA scout: a league's available players worth keeping for the rest of the season. Signed in,
 * and served only where {@code FA_SCOUT_ENABLED} and the AI projection say so; elsewhere its route
 * is a 404.
 */
@RestController
@Validated
@RequestMapping("/api/v1/fa-scout")
@Tag(name = "FA scout", description = "Available players worth keeping for the rest of the season")
public class FaScoutController {

    private final FaScoutService scoutService;

    public FaScoutController(FaScoutService scoutService) {
        this.scoutService = scoutService;
    }

    @Operation(
            operationId = "faScoutFreeAgents",
            summary = "The players a league has available, with the model's rest of the season",
            description = "Each player carries the model's rest of the season and its line for him "
                    + "before the season began, both in the projection's own stat vocabulary: score "
                    + "them with the league's scoring settings. Joined on identity, so the answer "
                    + "does not depend on which platform the player pool is served from.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Available players the model projects"),
        @ApiResponse(responseCode = "404", description = "The FA scout is off in this environment")
    })
    @GetMapping("/free-agents")
    public ScoutListResponse freeAgents(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "Which platform's league to read") @RequestParam PlayerIdSpace platform,
            @Parameter(description = "The Yahoo league key or the ESPN league id")
                    @RequestParam @NotBlank @Size(max = 64) String leagueId) {
        return scoutService.freeAgents(userId, platform, leagueId);
    }

    @Operation(
            operationId = "faScoutMyTeam",
            summary = "The user's own team in a league, with the model's rest of the season",
            description = "Each player on the user's team today, bench and injured reserve included, "
                    + "with the model's rest of the season on the same line the available players "
                    + "carry: score both together to weigh a pickup against the player it would "
                    + "replace. Read live from the platform.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The user's team, or found=false"),
        @ApiResponse(responseCode = "404", description = "The FA scout is off in this environment")
    })
    @GetMapping("/my-team")
    public ScoutMyTeamResponse myTeam(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "Which platform's league to read") @RequestParam PlayerIdSpace platform,
            @Parameter(description = "The Yahoo league key or the ESPN league id")
                    @RequestParam @NotBlank @Size(max = 64) String leagueId) {
        return scoutService.myTeam(userId, platform, leagueId);
    }
}
