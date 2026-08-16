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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "Players", description = "NHL player stats endpoints (public, read-only)")
public class PlayerController {

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
        return ResponseEntity.ok(playerService.getSkaters());
    }

    @GetMapping("/goalies")
    @Operation(summary = "Get all goalies", description = "Returns all goalies with full season stats")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goalies retrieved successfully"),
            @ApiResponse(responseCode = "502", description = "NHL service unavailable")
    })
    public ResponseEntity<List<GoalieResponse>> getGoalies() {
        return ResponseEntity.ok(playerService.getGoalies());
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
}
