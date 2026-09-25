package com.fantasy.bff.controller;

import com.fantasy.bff.dto.response.ErrorDto;
import com.fantasy.bff.dto.response.LeagueSummaryResponse;
import com.fantasy.bff.dto.response.SummarySource;
import com.fantasy.bff.service.LeagueSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where a league stands, for a manager who plays on the platform rather than building a board here.
 *
 * <p>The summary is computed in full and then cut to what the account may see: the teams and
 * their totals for everyone, the players behind them only with premium. It is a read of the
 * user's own league — the rosters, the teams and the scoring settings all come from Yahoo, none
 * of them from the caller — so no request can shape the rosters into a per-player readout of
 * the model's lines.
 */
@Tag(name = "League summaries", description = "Where a league's teams stand")
@RestController
@RequestMapping("/api/v1/league-summaries")
public class LeagueSummaryController {

    private final LeagueSummaryService service;

    public LeagueSummaryController(LeagueSummaryService service) {
        this.service = service;
    }

    @Operation(
            summary = "Total a Yahoo league's teams against a projection",
            description = "Reads the league's rosters, draft and scoring settings from Yahoo and totals "
                    + "every team's current roster, or its picks until the draft is over. The teams, their totals and where each one places are "
                    + "returned to any signed-in manager; the roster rows and lineups behind those "
                    + "totals need premium, and are absent without it. 404 where this environment "
                    + "does not offer reading a league's draft, or where the AI projection is off "
                    + "and the model was asked for.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The league's teams, totalled"),
            @ApiResponse(responseCode = "400", description = "Unknown source",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Not offered here, the AI projection is off, or the user has not connected Yahoo",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "424", description = YahooController.REFUSED,
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/yahoo/{leagueKey}")
    public LeagueSummaryResponse yahooLeague(
            @AuthenticationPrincipal String userId,
            @PathVariable @Size(max = 64) String leagueKey,
            @Parameter(description = "Which projection to score the players against; the model by default")
            @RequestParam(required = false) @Size(max = 32) String source) {
        LeagueSummaryService.Result result =
                service.summarise(userId, leagueKey, SummarySource.from(source));
        return LeagueSummaryResponse.from(
                result.summary(),
                result.source(),
                result.modelVersion(),
                result.premium(),
                result.scoringType(),
                result.status(),
                result.picks());
    }
}
