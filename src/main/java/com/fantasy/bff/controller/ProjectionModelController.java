package com.fantasy.bff.controller;

import com.fantasy.bff.dto.request.GameRange;
import com.fantasy.bff.dto.response.PlayerSplitResponse;
import com.fantasy.bff.dto.response.SeededProjectionResponse;
import com.fantasy.bff.exception.PremiumRequiredException;
import com.fantasy.bff.service.AiProjectionAvailability;
import com.fantasy.bff.service.EntitlementService;
import com.fantasy.bff.service.PlayerSplitService;
import com.fantasy.bff.service.ProjectionSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private static final int MAX_LIMIT = 500;

    /**
     * The stretch a free account may measure, in team game numbers. Every NHL season is the same
     * 82 games, so "the last five" is games 78-82 for every team in every season — the same
     * constant the web resolves its presets against, and the reason this needs no lookup.
     */
    private static final int SEASON_SCHEDULE_GAMES = 82;

    private static final int FREE_RANGE_LENGTH = 5;

    private static final int FIRST_FREE_GAME = SEASON_SCHEDULE_GAMES - FREE_RANGE_LENGTH + 1;

    /**
     * The slice of the board a free account may read: the top of it, and no more than the
     * new-projection page needs to draw its five-row preview. Wide enough that the page can rank
     * those rows by its own default weights rather than taking the order it is served in, narrow
     * enough that what leaves the server is a teaser — the handful of names everyone already
     * agrees on — and not the board, which is what premium buys.
     *
     * <p>Both limits have to be asked for. An absent one means "all of them", which is the whole
     * model however small the other one is.
     */
    private static final int FREE_PREVIEW_SKATERS = 25;

    private static final int FREE_PREVIEW_GOALIES = 10;

    private final ProjectionSeedService seedService;
    private final PlayerSplitService splitService;
    private final AiProjectionAvailability aiProjection;
    private final EntitlementService entitlementService;
    private final int defaultSeason;
    private final String defaultModelVersion;

    public ProjectionModelController(
            ProjectionSeedService seedService,
            PlayerSplitService splitService,
            AiProjectionAvailability aiProjection,
            EntitlementService entitlementService,
            @Value("${services.projection.season}") int defaultSeason,
            @Value("${services.projection.model-version}") String defaultModelVersion) {
        this.seedService = seedService;
        this.splitService = splitService;
        this.aiProjection = aiProjection;
        this.entitlementService = entitlementService;
        this.defaultSeason = defaultSeason;
        this.defaultModelVersion = defaultModelVersion;
    }

    @Operation(
            summary = "Generate projection lines for every mapped player",
            description =
                    "Returns the model's projected stat lines keyed by this platform's player id, "
                            + "ready to be saved as a new projection. Scoring settings are not "
                            + "included — those belong to the user's league, so the client supplies "
                            + "them when saving. Needs a premium subscription: these are the model's own lines, "
                            + "unless both limits are small enough to be the preview a free "
                            + "account is shown (at most "
                            + FREE_PREVIEW_SKATERS
                            + " skaters and "
                            + FREE_PREVIEW_GOALIES
                            + " goalies). `skaterLimit` and `goalieLimit` return only the "
                            + "top of the board — skaters by projected points, goalies by projected "
                            + "wins, the same order the player pool is served in — for a caller "
                            + "drawing a preview rather than seeding a projection. They trim the "
                            + "rows and nothing else: the counts still report everything the model "
                            + "reached, so a five-row preview can still say how much of the league "
                            + "that is.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Generated lines plus a summary of what was skipped"),
        @ApiResponse(responseCode = "400", description = "A limit is not between 1 and 500"),
        @ApiResponse(responseCode = "403", description = "More than the free preview needs premium and this account has none"),
        @ApiResponse(responseCode = "404", description = "The AI projection is switched off")
    })
    @GetMapping("/seed")
    public SeededProjectionResponse seed(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "Season to project; defaults to the configured one")
                    @RequestParam(required = false)
                    Integer season,
            @Parameter(description = "How many skater lines to return; all of them when absent")
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(MAX_LIMIT)
                    Integer skaterLimit,
            @Parameter(description = "How many goalie lines to return; all of them when absent")
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(MAX_LIMIT)
                    Integer goalieLimit) {
        // These are the model's own lines, which is the whole of what the AI projection is. The
        // splits below are not: they are measured totals behind Who's hot, and stay up either way.
        if (!aiProjection.available()) {
            throw new NoSuchElementException("The AI projection is not enabled");
        }
        // Every row here is the model talking, which is the thing premium pays for — all of it
        // except the top few, which the new-projection page draws as a preview for everyone. A
        // starting point nobody may look at is a hard thing to want.
        if (!isPreviewSized(skaterLimit, goalieLimit) && !entitlementService.hasPremiumAccess(userId)) {
            throw new PremiumRequiredException(
                    "The AI projection is part of premium. Subscribe to see the model's lines.");
        }
        int target = season == null ? defaultSeason : season;
        ProjectionSeedService.Seed seed =
                seedService.seed(target, defaultModelVersion, skaterLimit, goalieLimit);
        return new SeededProjectionResponse(
                seed.players(),
                target,
                // What came back, not what was asked for: with nothing pinned the version is
                // projection-service's to choose, so echoing the request would be a guess.
                seed.modelVersion(),
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
                            + "with neither, the whole season is covered. Reading further back than "
                            + "the last 5 games needs a premium subscription.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Totals over the range, highest scoring first"),
            @ApiResponse(responseCode = "403", description = "The range needs premium and this account has none")
    })
    @GetMapping("/splits/skaters")
    public List<PlayerSplitResponse> skaterSplits(
            @AuthenticationPrincipal String userId,
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
        GameRange range = new GameRange(fromGame, toGame, lastGames);
        requireEntitlementFor(userId, range);
        return splitService.skaterSplits(splitSeason(season), range, limit);
    }

    @Operation(
            summary = "Goalies' measured totals over a range of a season's games",
            description = "Reading further back than the last 5 games needs a premium subscription.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Totals over the range"),
            @ApiResponse(responseCode = "403", description = "The range needs premium and this account has none")
    })
    @GetMapping("/splits/goalies")
    public List<PlayerSplitResponse> goalieSplits(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) @Min(1) @Max(84) Integer fromGame,
            @RequestParam(required = false) @Min(1) @Max(84) Integer toGame,
            @RequestParam(required = false) @Min(1) @Max(84) Integer lastGames,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        GameRange range = new GameRange(fromGame, toGame, lastGames);
        requireEntitlementFor(userId, range);
        return splitService.goalieSplits(splitSeason(season), range, limit);
    }

    /**
     * Whether this is the preview and not the board: the top of both lists, trimmed to what the
     * new-projection page asks for. Judged before the subscription is looked up, so the request
     * every free account makes on that page costs no call to db-service.
     */
    private static boolean isPreviewSized(Integer skaterLimit, Integer goalieLimit) {
        return skaterLimit != null
                && goalieLimit != null
                && skaterLimit <= FREE_PREVIEW_SKATERS
                && goalieLimit <= FREE_PREVIEW_GOALIES;
    }

    /**
     * Picking which stretch of the season to measure is what premium buys; the most recent form
     * is free. The web draws the same line, but a switch in a browser is an offer withdrawn
     * rather than a refusal — this endpoint is reachable without it.
     *
     * <p>The range is judged before the subscription is looked up, so the free case (every
     * request the web makes on behalf of an account that has not paid) costs no call to
     * db-service.
     */
    private void requireEntitlementFor(String userId, GameRange range) {
        if (isFree(range) || entitlementService.hasPremiumAccess(userId)) {
            return;
        }
        throw new PremiumRequiredException(
                "A free account can measure the last " + FREE_RANGE_LENGTH
                        + " games. Choosing any other range needs a premium subscription.");
    }

    /**
     * Whether the range asks for nothing a free account cannot already see. A shorter window
     * inside the free one passes: it reveals no game the account is not entitled to, and holding
     * it to exactly five would turn every off-by-one between client and server into a refusal.
     *
     * <p>An open range means the whole season, which is not it.
     */
    private static boolean isFree(GameRange range) {
        if (range.lastGames() != null) {
            return range.lastGames() <= FREE_RANGE_LENGTH;
        }
        if (range.fromGame() == null || range.toGame() == null) {
            return false;
        }
        return range.fromGame() >= FIRST_FREE_GAME
                && range.toGame() - range.fromGame() + 1 <= FREE_RANGE_LENGTH;
    }

    /** Splits default one season back from the projected one — that is the season with games in it. */
    private int splitSeason(Integer season) {
        return season == null ? defaultSeason - 1 : season;
    }
}
