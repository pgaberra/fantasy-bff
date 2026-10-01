package com.fantasy.bff.controller;

import com.fantasy.bff.dto.response.RosteredPlayersResponse;
import com.fantasy.bff.service.LeagueRosteredPlayersService;
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
 * The players a league's teams hold, for Who's hot to leave out when it shows only who is
 * available. Signed in, not premium, and served only where
 * {@code WHOS_HOT_AVAILABLE_FILTER_ENABLED} says so; elsewhere it is a 404.
 */
@RestController
@Validated
@RequestMapping("/api/v1/leagues/rostered-players")
@Tag(name = "Leagues", description = "What a linked fantasy league holds")
public class LeagueRosteredPlayersController {

    private final LeagueRosteredPlayersService service;

    public LeagueRosteredPlayersController(LeagueRosteredPlayersService service) {
        this.service = service;
    }

    @Operation(
            operationId = "leagueRosteredPlayers",
            summary = "Every player on a roster in a league",
            description = "Read from the league today, so a drop or a pickup since the draft shows. "
                    + "Ids are the player pool's, so they match the rows of every board.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The rostered players' ids"),
        @ApiResponse(responseCode = "404", description = "The filter is off in this environment")
    })
    @GetMapping
    public RosteredPlayersResponse rosteredPlayers(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "Which platform's league to read") @RequestParam PlayerIdSpace platform,
            @Parameter(description = "The Yahoo league key or the ESPN league id")
                    @RequestParam @NotBlank @Size(max = 64) String leagueId) {
        return service.rostered(userId, platform, leagueId);
    }
}
