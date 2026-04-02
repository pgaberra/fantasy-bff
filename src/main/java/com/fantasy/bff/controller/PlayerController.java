package com.fantasy.bff.controller;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "Players", description = "NHL player stats endpoints")
@SecurityRequirement(name = "bearerAuth")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping("/skaters")
    @Operation(summary = "Get all skaters", description = "Returns all skaters with full season stats")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Skaters retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "502", description = "NHL service unavailable")
    })
    public ResponseEntity<List<SkaterResponse>> getSkaters() {
        return ResponseEntity.ok(playerService.getSkaters());
    }

    @GetMapping("/goalies")
    @Operation(summary = "Get all goalies", description = "Returns all goalies with full season stats")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goalies retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "502", description = "NHL service unavailable")
    })
    public ResponseEntity<List<GoalieResponse>> getGoalies() {
        return ResponseEntity.ok(playerService.getGoalies());
    }
}
