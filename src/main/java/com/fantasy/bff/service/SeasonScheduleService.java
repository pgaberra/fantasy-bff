package com.fantasy.bff.service;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.generated.projection.model.RatedGameResponse;
import com.fantasy.bff.generated.projection.model.ScheduleStrengthResponse;
import com.fantasy.bff.generated.projection.model.ScheduleWeekResponse;
import com.fantasy.bff.generated.projection.model.ScheduleWeeksResponse;
import com.fantasy.bff.generated.projection.model.TeamScheduleResponse;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import com.fantasy.bff.service.scoring.LeagueSchedule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The NHL schedule a league's lineups are set against: every club's game dates in the newest
 * published season, from projection-service.
 *
 * <p>projection-service hands the schedule out a month at a time (its streaming rating caps a
 * stretch at 31 days), so the season is read in month-sized pieces and kept for a few hours: the
 * schedule moves only when the league postpones a game.
 */
@Service
public class SeasonScheduleService {

    /** The NHL's dates are Eastern: a night's games belong to it until they are played. */
    static final ZoneId NHL_ZONE = ZoneId.of("America/New_York");

    /** The longest stretch projection-service rates in one call. */
    static final int STRETCH_DAYS = 31;

    private static final Duration FRESH_FOR = Duration.ofHours(6);

    private final ProjectionServiceClient projectionServiceClient;
    private final Clock clock;

    private Season season;
    private Instant readAt;

    @Autowired
    public SeasonScheduleService(ProjectionServiceClient projectionServiceClient) {
        this(projectionServiceClient, Clock.system(NHL_ZONE));
    }

    SeasonScheduleService(ProjectionServiceClient projectionServiceClient, Clock clock) {
        this.projectionServiceClient = projectionServiceClient;
        this.clock = clock;
    }

    /**
     * The schedule a league is ranked against today: the rest of the season once it is under way,
     * from tonight on, and the whole season before its first game or after its last.
     *
     * @throws IllegalStateException where projection-service has no schedule published
     */
    public LeagueSchedule schedule() {
        Season current = season();
        LocalDate today = LocalDate.now(clock);
        boolean underWay = !today.isBefore(current.start()) && !today.isAfter(current.end());
        Map<String, List<LocalDate>> window = new HashMap<>();
        Map<String, Integer> seasonGames = new HashMap<>();
        current.games().forEach((club, dates) -> {
            window.put(club, underWay ? dates.stream().filter(date -> !date.isBefore(today)).toList() : dates);
            seasonGames.put(club, dates.size());
        });
        return new LeagueSchedule(window, seasonGames);
    }

    /** Every club's game dates, by the pool's spelling of the club, from opening night to the last. */
    private record Season(LocalDate start, LocalDate end, Map<String, List<LocalDate>> games) {
    }

    private synchronized Season season() {
        Instant now = clock.instant();
        if (season == null || !now.isBefore(readAt.plus(FRESH_FOR))) {
            season = read();
            readAt = now;
        }
        return season;
    }

    private Season read() {
        ScheduleWeeksResponse weeks = projectionServiceClient.scheduleWeeks();
        if (weeks == null || weeks.getWeeks().isEmpty()) {
            throw new IllegalStateException("projection-service has no published schedule to set lineups against");
        }
        LocalDate start = weeks.getWeeks().stream().map(ScheduleWeekResponse::getStart).min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate end = weeks.getWeeks().stream().map(ScheduleWeekResponse::getEnd).max(LocalDate::compareTo)
                .orElseThrow();

        Map<String, TreeSet<LocalDate>> dates = new HashMap<>();
        for (LocalDate from = start; !from.isAfter(end); from = from.plusDays(STRETCH_DAYS)) {
            LocalDate to = from.plusDays(STRETCH_DAYS - 1L);
            ScheduleStrengthResponse stretch = projectionServiceClient.scheduleStrength(from, to.isAfter(end) ? end : to);
            if (stretch == null) {
                throw new IllegalStateException("projection-service returned no schedule for " + from);
            }
            for (TeamScheduleResponse team : stretch.getTeams()) {
                TreeSet<LocalDate> clubDates =
                        dates.computeIfAbsent(PlayerIdResolver.platformTeam(team.getTeam()), club -> new TreeSet<>());
                for (RatedGameResponse game : team.getSchedule()) {
                    clubDates.add(game.getDate());
                }
            }
        }
        Map<String, List<LocalDate>> games = new HashMap<>();
        dates.forEach((club, clubDates) -> games.put(club, List.copyOf(clubDates)));
        return new Season(start, end, Map.copyOf(games));
    }
}
