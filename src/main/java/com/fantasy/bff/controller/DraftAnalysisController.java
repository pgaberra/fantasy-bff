package com.fantasy.bff.controller;

import com.fantasy.bff.dto.response.DraftAnalysisResponse;
import com.fantasy.bff.dto.response.ErrorDto;
import com.fantasy.bff.service.DraftAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Draft Analysis: a league's draft with every pick graded against the model. Signed in, and served
 * only where {@code DRAFT_ANALYSIS_ENABLED}, the AI projection and reading a league's draft all say
 * so; elsewhere its route is a 404.
 */
@RestController
@Validated
@RequestMapping("/api/v1/draft-analysis")
@Tag(name = "Draft analysis", description = "A league's draft, every pick graded against the model")
public class DraftAnalysisController {

    private final DraftAnalysisService service;

    public DraftAnalysisController(DraftAnalysisService service) {
        this.service = service;
    }

    @Operation(
            operationId = "yahooDraftAnalysis",
            summary = "Grade a Yahoo league's draft against the model",
            description = "Reads the league's draft and scoring settings from Yahoo and sets every pick "
                    + "made so far against the model's ranking under the league's own scoring: the line "
                    + "the model froze on the eve of the season where it is stored, its current season "
                    + "line otherwise. The teams and their sums are returned to any signed-in manager; "
                    + "each pick's rank, value, grade and best available need premium and are absent "
                    + "without it.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The draft, graded"),
        @ApiResponse(responseCode = "404",
                description = "Draft Analysis is off here, or the user has not connected Yahoo",
                content = @Content(schema = @Schema(implementation = ErrorDto.class))),
        @ApiResponse(responseCode = "424", description = YahooController.REFUSED,
                content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/yahoo/{leagueKey}")
    public DraftAnalysisResponse yahoo(
            @AuthenticationPrincipal String userId,
            @PathVariable @Size(max = 64) String leagueKey) {
        return service.yahoo(userId, leagueKey);
    }
}
