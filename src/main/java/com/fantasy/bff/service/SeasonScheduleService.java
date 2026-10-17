package com.fantasy.bff.service;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.dto.response.ClubNight;
import com.fantasy.bff.dto.response.OpponentWorth;
import com.fantasy.bff.generated.projection.model.RatedGameResponse;
import com.fantasy.bff.generated.projection.model.ScheduleStrengthResponse;
import com.fantasy.bff.generated.projection.model.ScheduleWeekResponse;
import com.fantasy.bff.generated.projection.model.ScheduleWeeksResponse;
import com.fantasy.bff.generated.projection.model.TeamScheduleResponse;
import com.fantasy.bff.service.mapping.ModelStatMapping;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import com.fantasy.bff.service.scoring.LeagueSchedule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
        Map<String, List<LocalDate>> window = new HashMap<>();
        Map<String, Integer> seasonGames = new HashMap<>();
        current.games().forEach((club, games) -> {
            window.put(club, inWindow(current, games).stream().map(ClubNight::date).toList());
            seasonGames.put(club, games.size());
        });
        return new LeagueSchedule(window, seasonGames);
    }

    /**
     * The games of the same window as {@link #schedule()}, each with its opponent and venue, and
     * what a game against each opponent at each venue is worth to a player's line today.
     *
     * @throws IllegalStateException where projection-service has no schedule published
     */
    public SeasonNights nights() {
        Season current = season();
        Map<String, List<ClubNight>> window = new HashMap<>();
        current.games().forEach((club, games) -> window.put(club, inWindow(current, games)));
        return new SeasonNights(window, current.worth());
    }

    /**
     * Every club's games over the window, by the pool's spelling of the club, and what a game
     * against each opponent at each venue is worth.
     */
    public record SeasonNights(Map<String, List<ClubNight>> window, List<OpponentWorth> worth) {

        public SeasonNights {
            window = window.entrySet().stream().collect(
                    Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
            worth = List.copyOf(worth);
        }
    }

    private List<ClubNight> inWindow(Season current, List<ClubNight> games) {
        LocalDate today = LocalDate.now(clock);
        boolean underWay = !today.isBefore(current.start()) && !today.isAfter(current.end());
        return underWay ? games.stream().filter(game -> !game.date().isBefore(today)).toList() : games;
    }

    /**
     * Every club's games, by the pool's spelling of the club, from opening night to the last, and
     * what a game against each opponent at each venue is worth.
     */
    private record Season(
            LocalDate start, LocalDate end, Map<String, List<ClubNight>> games, List<OpponentWorth> worth) {
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

        Map<LocalDate, ScheduleStrengthResponse> pieces = new HashMap<>();
        Map<String, TreeMap<LocalDate, ClubNight>> nights = new HashMap<>();
        for (LocalDate from = start; !from.isAfter(end); from = from.plusDays(STRETCH_DAYS)) {
            ScheduleStrengthResponse piece = stretch(from, end);
            pieces.put(from, piece);
            for (TeamScheduleResponse team : piece.getTeams()) {
                TreeMap<LocalDate, ClubNight> clubNights =
                        nights.computeIfAbsent(PlayerIdResolver.platformTeam(team.getTeam()), club -> new TreeMap<>());
                for (RatedGameResponse game : team.getSchedule()) {
                    clubNights.put(game.getDate(), new ClubNight(
                            game.getDate(), game.getOpponent(), Boolean.TRUE.equals(game.getHome())));
                }
            }
        }
        Map<String, List<ClubNight>> games = new HashMap<>();
        nights.forEach((club, clubNights) -> games.put(club, List.copyOf(clubNights.values())));
        return new Season(start, end, Map.copyOf(games), worth(start, end, pieces));
    }

    /** One stretch of the season from {@code from}, a month long or to its end. */
    private ScheduleStrengthResponse stretch(LocalDate from, LocalDate end) {
        LocalDate to = from.plusDays(STRETCH_DAYS - 1L);
        ScheduleStrengthResponse stretch = projectionServiceClient.scheduleStrength(from, to.isAfter(end) ? end : to);
        if (stretch == null) {
            throw new IllegalStateException("projection-service returned no schedule for " + from);
        }
        return stretch;
    }

    /**
     * What a game against each opponent at each venue is worth, as projection-service rates the
     * month from today.
     *
     * <p>Not read off the month-long pieces above: projection-service rates a stretch's opponents
     * as they stood the morning it starts, so the piece starting on opening night would rate the
     * rest of October on last season's numbers. And a game's worth to each stat is the opponent's
     * and the venue's alone (projection-service's {@code range_projection.game_worth} reads
     * nothing else), so a month in which every club plays home and away names every pairing.
     * Before opening night and after the last game the month read is the season's first, and a
     * month the season was already read in is not asked for again.
     */
    private List<OpponentWorth> worth(
            LocalDate start, LocalDate end, Map<LocalDate, ScheduleStrengthResponse> pieces) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.isBefore(start) || today.isAfter(end) ? start : today;
        Map<String, OpponentWorth> byPairing = new LinkedHashMap<>();
        ScheduleStrengthResponse month = pieces.containsKey(from) ? pieces.get(from) : stretch(from, end);
        for (TeamScheduleResponse team : month.getTeams()) {
            for (RatedGameResponse game : team.getSchedule()) {
                if (game.getOpponent() == null) {
                    continue;
                }
                boolean home = Boolean.TRUE.equals(game.getHome());
                byPairing.putIfAbsent(game.getOpponent() + "/" + home, new OpponentWorth(
                        game.getOpponent(),
                        home,
                        ModelStatMapping.statWorth(game.getStatWorth()),
                        number(game.getPlusMinusPerMinute())));
            }
        }
        return byPairing.values().stream()
                .sorted(Comparator.comparing(OpponentWorth::opponent).thenComparing(OpponentWorth::home))
                .toList();
    }

    private static double number(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }
}
