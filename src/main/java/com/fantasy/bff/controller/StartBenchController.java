package com.fantasy.bff.controller;

import com.fantasy.bff.dto.response.PlannerMyTeamResponse;
import com.fantasy.bff.service.PlayerIdSpace;
import com.fantasy.bff.service.StartBenchAvailability;
import com.fantasy.bff.service.StreamerPlannerMyTeamService;
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
 * Start/Bench: the user's lineup for one day, so the client can say who should start and who sit.
 * Signed in, not premium, and served only where {@code START_BENCH_ENABLED} and the AI projection
 * say so; elsewhere its route is a 404.
 *
 * <p>The client asks for the date it shows, its own today by default, rather than the server
 * deciding which day "today" is: a user in Stockholm and the NHL's Eastern time disagree for six
 * hours a night.
 */
@RestController
@Validated
@RequestMapping("/api/v1/start-bench")
@Tag(name = "Start/Bench", description = "The user's lineup for a day, with the model's line for each player")
public class StartBenchController {

    private final StreamerPlannerMyTeamService myTeamService;
    private final StartBenchAvailability availability;

    public StartBenchController(StreamerPlannerMyTeamService myTeamService, StartBenchAvailability availability) {
        this.myTeamService = myTeamService;
        this.availability = availability;
    }

    @Operation(
            operationId = "startBenchMyTeam",
            summary = "The user's own team in a league as set for a day",
            description = "Read live from the platform: each player's club, the positions the league "
                    + "may start him at, the slot he holds that day (for a day ahead, what the "
                    + "manager has set for it) and whether he is out. Each player the model projects "
                    + "also carries its line for that day alone, on a free agent's scale: his one "
                    + "game, if his club plays, weighed by the chance he dresses, or starts for a "
                    + "goalie.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The team, or found=false when none is the user's"),
        @ApiResponse(responseCode = "400", description = "The date is missing or not a date"),
        @ApiResponse(responseCode = "404", description = "Start/Bench is off in this environment"),
    })
    @GetMapping("/my-team")
    public PlannerMyTeamResponse myTeam(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "Which platform's league to read") @RequestParam PlayerIdSpace platform,
            @Parameter(description = "The Yahoo league key or the ESPN league id")
                    @RequestParam @NotBlank @Size(max = 64) String leagueId,
            @Parameter(description = "The day, e.g. 2026-10-13")
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        availability.require();
        return myTeamService.onDate(userId, platform, leagueId, date);
    }
}
