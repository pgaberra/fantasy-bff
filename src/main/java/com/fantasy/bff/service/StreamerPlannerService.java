package com.fantasy.bff.service;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.config.StreamerPlannerProperties;
import com.fantasy.bff.dto.response.PlannerWeek;
import com.fantasy.bff.dto.response.PlannerWeeksResponse;
import com.fantasy.bff.dto.response.ScheduleNight;
import com.fantasy.bff.dto.response.ScheduleStrengthResponse;
import com.fantasy.bff.dto.response.ScheduledGame;
import com.fantasy.bff.dto.response.TeamSchedule;
import com.fantasy.bff.generated.projection.model.RatedGameResponse;
import com.fantasy.bff.generated.projection.model.ScheduleWeeksResponse;
import com.fantasy.bff.generated.projection.model.TeamScheduleResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * The streamer planner: the season's weeks, and how good every NHL team's schedule is over one.
 * The rating is projection-service's, read off the published schedule; this checks the switch and
 * the stretch, and maps the answer into the BFF's own types.
 */
@Service
public class StreamerPlannerService {

    /** The longest stretch the planner rates, as projection-service caps it. */
    static final int MAX_STRETCH_DAYS = 31;

    private final ProjectionServiceClient projectionServiceClient;
    private final StreamerPlannerProperties properties;

    public StreamerPlannerService(
            ProjectionServiceClient projectionServiceClient, StreamerPlannerProperties properties) {
        this.projectionServiceClient = projectionServiceClient;
        this.properties = properties;
    }

    public PlannerWeeksResponse weeks() {
        requireAvailable();
        ScheduleWeeksResponse answer = projectionServiceClient.scheduleWeeks();
        if (answer == null) {
            return new PlannerWeeksResponse(null, null, List.of());
        }
        return new PlannerWeeksResponse(
                answer.getSeason(),
                answer.getCurrentWeek(),
                answer.getWeeks().stream()
                        .map(week -> new PlannerWeek(week.getWeek(), week.getStart(), week.getEnd(), week.getGames()))
                        .toList());
    }

    public ScheduleStrengthResponse strength(LocalDate start, LocalDate end) {
        requireAvailable();
        // Checked here as well as downstream: a service validates its own input.
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_STRETCH_DAYS) {
            throw new IllegalArgumentException("A stretch may cover at most " + MAX_STRETCH_DAYS + " days");
        }
        var answer = projectionServiceClient.scheduleStrength(start, end);
        if (answer == null) {
            throw new IllegalStateException("projection-service returned no schedule");
        }
        return new ScheduleStrengthResponse(
                answer.getSeason(),
                answer.getStart(),
                answer.getEnd(),
                answer.getOffNightMaxGames(),
                answer.getNights().stream()
                        .map(night -> new ScheduleNight(night.getDate(), night.getGames(), night.getOffNight()))
                        .toList(),
                answer.getTeams().stream().map(StreamerPlannerService::team).toList());
    }

    private void requireAvailable() {
        if (!properties.enabled()) {
            throw new NoSuchElementException("The streamer planner is not available");
        }
    }

    private static TeamSchedule team(TeamScheduleResponse team) {
        return new TeamSchedule(
                team.getTeam(),
                team.getGames(),
                team.getOffNightGames(),
                team.getBackToBacks(),
                team.getHomeGames(),
                number(team.getSkaterScore()),
                team.getSkaterRank(),
                number(team.getGoalieScore()),
                team.getGoalieRank(),
                team.getSchedule().stream().map(StreamerPlannerService::game).toList());
    }

    private static ScheduledGame game(RatedGameResponse game) {
        return new ScheduledGame(
                game.getDate(),
                game.getOpponent(),
                game.getHome(),
                game.getOffNight(),
                game.getBackToBack(),
                number(game.getOpponentGoalsAgainst()),
                number(game.getOpponentGoalsFor()));
    }

    private static double number(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }
}
