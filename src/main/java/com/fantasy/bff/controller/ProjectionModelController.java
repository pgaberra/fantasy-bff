package com.fantasy.bff.controller;

import com.fantasy.bff.dto.request.GameRange;
import com.fantasy.bff.dto.response.PlayerSplitResponse;
import com.fantasy.bff.dto.response.SeededProjectionResponse;
import com.fantasy.bff.service.PlayerSplitService;
import com.fantasy.bff.service.ProjectionSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Projection model",
        description =
                "Model-generated projection lines, and measured totals over a range of games. "
                        + "Data © MoneyPuck.com, used under its non-commercial terms.")
@RestController
@RequestMapping("/api/v1/projection-model")
@Validated
public class ProjectionModelController {

    private final ProjectionSeedService seedService;
    private final PlayerSplitService splitService;
    private final int defaultSeason;
    private final String defaultModelVersion;

    public ProjectionModelController(
            ProjectionSeedService seedService,
            PlayerSplitService splitService,
            @Value("${services.projection.season}") int defaultSeason,
            @Value("${services.projection.model-version}") String defaultModelVersion) {
        this.seedService = seedService;
        this.splitService = splitService;
        this.defaultSeason = defaultSeason;
        this.defaultModelVersion = defaultModelVersion;
    }

    @Operation(
            summary = "Generate projection lines for every mapped player",
            description =
                    "Returns the model's projected stat lines keyed by this platform's player id, "
                            + "ready to be saved as a new projection. Scoring settings are not "
                            + "included — those belong to the user's league, so the client supplies "
                            + "them when saving.")
    @ApiResponse(responseCode = "200", description = "Generated lines plus a summary of what was skipped")
    @GetMapping("/seed")
    public SeededProjectionResponse seed(
            @Parameter(description = "Season to project; defaults to the configured one")
                    @RequestParam(required = false)
                    Integer season) {
        int target = season == null ? defaultSeason : season;
        ProjectionSeedService.Seed seed = seedService.seed(target, defaultModelVersion);
        return new SeededProjectionResponse(
                seed.players(),
                target,
                defaultModelVersion,
                seed.skatersSeeded(),
                seed.goaliesSeeded(),
                seed.unmapped(),
                seed.withoutWorkload());
    }

    @Operation(
            summary = "Skaters' measured totals over a range of a season's games",
            description =
                    "What players actually produced over a stretch of their team's schedule — "
                            + "measured, not projected. Ranges are in team game numbers, so the same "
                            + "range covers the same stretch for every player; one who missed some "
                            + "of them shows fewer games. Give either fromGame/toGame or lastGames; "
                            + "with neither, the whole season is covered.")
    @ApiResponse(responseCode = "200", description = "Totals over the range, highest scoring first")
    @GetMapping("/splits/skaters")
    public List<PlayerSplitResponse> skaterSplits(
            @RequestParam(required = false) Integer season,
            @Parameter(description = "First team game in the range (1-based)")
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(84)
                    Integer fromGame,
            @Parameter(description = "Last team game in the range, inclusive")
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(84)
                    Integer toGame,
            @Parameter(description = "Shorthand for the team's final N games")
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(84)
                    Integer lastGames,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return splitService.skaterSplits(
                splitSeason(season), new GameRange(fromGame, toGame, lastGames), limit);
    }

    @Operation(summary = "Goalies' measured totals over a range of a season's games")
    @ApiResponse(responseCode = "200", description = "Totals over the range")
    @GetMapping("/splits/goalies")
    public List<PlayerSplitResponse> goalieSplits(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) @Min(1) @Max(84) Integer fromGame,
            @RequestParam(required = false) @Min(1) @Max(84) Integer toGame,
            @RequestParam(required = false) @Min(1) @Max(84) Integer lastGames,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return splitService.goalieSplits(
                splitSeason(season), new GameRange(fromGame, toGame, lastGames), limit);
    }

    /** Splits default one season back from the projected one — that is the season with games in it. */
    private int splitSeason(Integer season) {
        return season == null ? defaultSeason - 1 : season;
    }
}
