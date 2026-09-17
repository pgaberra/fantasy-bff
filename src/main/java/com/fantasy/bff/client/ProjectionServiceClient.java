package com.fantasy.bff.client;

import com.fantasy.bff.dto.request.GameRange;
import com.fantasy.bff.generated.projection.model.GoalieProjectionResponse;
import com.fantasy.bff.generated.projection.model.GoalieSplitResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.generated.projection.model.ScheduleStrengthResponse;
import com.fantasy.bff.generated.projection.model.ScheduleWeeksResponse;
import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.fantasy.bff.generated.projection.model.SkaterSplitResponse;
import com.fantasy.bff.generated.projection.model.SplitSeasonsResponse;
import java.time.LocalDate;
import java.util.List;

public interface ProjectionServiceClient {

    List<SkaterProjectionResponse> skaterProjections(int season, String modelVersion);

    List<GoalieProjectionResponse> goalieProjections(int season, String modelVersion);

    /**
     * Player identity — name, team, sweater number — for every player the NHL still lists as
     * active. The projections themselves carry only an NHL id, so this is what makes it
     * possible to work out which platform player a projection belongs to.
     */
    /**
     * @param season the season rookie status is wanted for, or null to leave it out
     */
    List<PlayerResponse> activePlayers(Integer season);

    /**
     * Players the NHL no longer lists as active — retired, or gone from the league.
     *
     * <p>Every player the store holds has real season history behind them, so an inactive one
     * is someone who played and stopped. A prospect who has never played an NHL game is not in
     * the store at all, which is what separates the two cases the platform cannot tell apart.
     */
    List<PlayerResponse> retiredPlayers();

    /**
     * Measured totals over a stretch of a team's schedule — what a player actually did over
     * that stretch, not a forecast.
     */
    /**
     * @param season the season to measure, or null for the newest season with a game played
     */
    List<SkaterSplitResponse> skaterSplits(Integer season, GameRange range, int limit);

    List<GoalieSplitResponse> goalieSplits(Integer season, GameRange range, int limit);

    /**
     * Every season a split can be measured over, with its length and how far it has got.
     *
     * @param targetSeason a season to list even before it has a game, or null
     */
    SplitSeasonsResponse splitSeasons(Integer targetSeason);

    /** The newest published season's Monday-Sunday weeks, numbered from opening night. */
    ScheduleWeeksResponse scheduleWeeks();

    /** Every club's games from {@code start} to {@code end} inclusive, scored and ranked for streaming. */
    ScheduleStrengthResponse scheduleStrength(LocalDate start, LocalDate end);
}
