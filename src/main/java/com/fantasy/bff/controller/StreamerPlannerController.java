package com.fantasy.bff.controller;

import com.fantasy.bff.dto.response.FreeAgentListResponse;
import com.fantasy.bff.dto.response.PlannerWeeksResponse;
import com.fantasy.bff.dto.response.ScheduleStrengthResponse;
import com.fantasy.bff.service.PlayerIdSpace;
import com.fantasy.bff.service.StreamerPlannerFreeAgentService;
import com.fantasy.bff.service.StreamerPlannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The streamer planner: which NHL teams have the best schedule in a week. Signed in, not premium,
 * and served only where {@code STREAMER_PLANNER_ENABLED} says so; elsewhere every route is a 404.
 */
@RestController
@Validated
@RequestMapping("/api/v1/streamer-planner")
@Tag(name = "Streamer planner", description = "Team schedules rated for streaming")
public class StreamerPlannerController {

    private final StreamerPlannerService plannerService;
    private final StreamerPlannerFreeAgentService freeAgentService;

    public StreamerPlannerController(
            StreamerPlannerService plannerService, StreamerPlannerFreeAgentService freeAgentService) {
        this.plannerService = plannerService;
        this.freeAgentService = freeAgentService;
    }

    @Operation(
            operationId = "streamerPlannerWeeks",
            summary = "The season's weeks",
            description = "Monday-Sunday weeks of the newest published NHL season, numbered from the week "
                    + "of opening night, and the week today falls in.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The weeks, in order"),
        @ApiResponse(responseCode = "404", description = "The streamer planner is off in this environment")
    })
    @GetMapping("/weeks")
    public PlannerWeeksResponse weeks() {
        return plannerService.weeks();
    }

    @Operation(
            operationId = "streamerPlannerTeams",
            summary = "Every team's schedule over a stretch, rated for streaming",
            description = "All NHL teams, best skater schedule first. The opponent rates are those known "
                    + "the morning of `start`. A stretch covers at most 31 days.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The teams, ranked"),
        @ApiResponse(responseCode = "400", description = "`end` is before `start`, or the stretch is too long"),
        @ApiResponse(responseCode = "404", description = "The streamer planner is off in this environment")
    })
    @GetMapping("/teams")
    public ScheduleStrengthResponse teams(
            @Parameter(description = "First date, e.g. 2026-10-12")
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "Last date, inclusive")
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return plannerService.strength(start, end);
    }

    @Operation(
            operationId = "streamerPlannerFreeAgents",
            summary = "The players a league has available, with the model's line for the stretch",
            description = "Joined on identity, so the answer does not depend on which platform the "
                    + "player pool is served from. The stats are the projection's own vocabulary: "
                    + "score them with the league's scoring settings.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Available players, best projected first"),
        @ApiResponse(responseCode = "400", description = "`end` is before `start`, or the stretch is too long"),
        @ApiResponse(responseCode = "404", description = "The streamer planner is off in this environment")
    })
    @GetMapping("/free-agents")
    public FreeAgentListResponse freeAgents(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "Which platform's league to read") @RequestParam PlayerIdSpace platform,
            @Parameter(description = "The Yahoo league key or the ESPN league id")
                    @RequestParam @NotBlank @Size(max = 64) String leagueId,
            @Parameter(description = "First date, e.g. 2026-10-12")
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "Last date, inclusive")
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return freeAgentService.freeAgents(userId, platform, leagueId, start, end);
    }
}
