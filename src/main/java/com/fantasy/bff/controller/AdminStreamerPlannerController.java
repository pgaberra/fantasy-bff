package com.fantasy.bff.controller;

import com.fantasy.bff.dto.response.FreeAgentListResponse;
import com.fantasy.bff.service.StreamerPlannerFreeAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The streamer planner's demo mode, for admins only (ROLE_ADMIN by its path in SecurityConfig, so
 * Premium does not open it): the free-agent list with no league behind it, the Yahoo pool under a
 * percent rostered standing in for a league's wire.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/streamer-planner")
@Tag(name = "Streamer planner", description = "Team schedules rated for streaming")
public class AdminStreamerPlannerController {

    private final StreamerPlannerFreeAgentService freeAgentService;

    public AdminStreamerPlannerController(StreamerPlannerFreeAgentService freeAgentService) {
        this.freeAgentService = freeAgentService;
    }

    @Operation(
            operationId = "streamerPlannerPoolFreeAgents",
            summary = "Admin demo: the Yahoo pool under a percent rostered, with the model's line for the stretch",
            description = "The same list as a league's free agents, read without a league: every "
                    + "pool player Yahoo rosters in at most `maxPercentRostered` percent of its "
                    + "leagues. A player Yahoo gives no percentage for is left out, and "
                    + "availability is UNKNOWN for all, since nobody is on waivers without a league.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pool players under the threshold"),
        @ApiResponse(responseCode = "400", description = "`end` is before `start`, the stretch is too "
                + "long, or the threshold is outside 0-100"),
        @ApiResponse(responseCode = "403", description = "The caller is not an admin"),
    })
    @GetMapping("/free-agents")
    public FreeAgentListResponse poolFreeAgents(
            @Parameter(description = "First date, e.g. 2026-10-12")
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "Last date, inclusive")
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @Parameter(description = "Highest Yahoo percent rostered to include, 0-100")
                    @RequestParam @DecimalMin("0") @DecimalMax("100") double maxPercentRostered) {
        return freeAgentService.poolFreeAgents(start, end, maxPercentRostered);
    }
}
