package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.generated.projection.model.RatedGameResponse;
import com.fantasy.bff.generated.projection.model.ScheduleStrengthResponse;
import com.fantasy.bff.generated.projection.model.ScheduleWeekResponse;
import com.fantasy.bff.generated.projection.model.ScheduleWeeksResponse;
import com.fantasy.bff.generated.projection.model.TeamScheduleResponse;
import com.fantasy.bff.service.scoring.LeagueSchedule;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeasonScheduleServiceTest {

    private static final LocalDate OPENING_NIGHT = LocalDate.of(2026, 10, 7);
    private static final LocalDate LAST_NIGHT = LocalDate.of(2026, 11, 20);

    private final ProjectionServiceClient client = mock(ProjectionServiceClient.class);

    @BeforeEach
    void setUp() {
        when(client.scheduleWeeks()).thenReturn(new ScheduleWeeksResponse().weeks(List.of(
                new ScheduleWeekResponse().week(1).start(OPENING_NIGHT).end(LocalDate.of(2026, 10, 11)).games(1),
                new ScheduleWeekResponse().week(2).start(LocalDate.of(2026, 10, 12)).end(LAST_NIGHT).games(3))));
        when(client.scheduleStrength(any(), any())).thenAnswer(call -> {
            LocalDate from = call.getArgument(0);
            LocalDate to = call.getArgument(1);
            return new ScheduleStrengthResponse().teams(List.of(
                    team("LAK", from, to, OPENING_NIGHT, LocalDate.of(2026, 10, 20), LAST_NIGHT),
                    team("TOR", from, to, LocalDate.of(2026, 11, 10))));
        });
    }

    /** A club's games that fall in the stretch asked for, as projection-service answers. */
    private static TeamScheduleResponse team(String club, LocalDate from, LocalDate to, LocalDate... dates) {
        return new TeamScheduleResponse()
                .team(club)
                .schedule(Stream.of(dates)
                        .filter(date -> !date.isBefore(from) && !date.isAfter(to))
                        .map(date -> new RatedGameResponse().date(date))
                        .toList());
    }

    private SeasonScheduleService on(LocalDate today) {
        return new SeasonScheduleService(
                client, Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), SeasonScheduleService.NHL_ZONE));
    }

    @Test
    @DisplayName("reads the season a month at a time and spells the clubs the way the pool does")
    void readsTheSeasonInMonths() {
        LeagueSchedule schedule = on(LocalDate.of(2026, 9, 1)).schedule();

        verify(client).scheduleStrength(OPENING_NIGHT, LocalDate.of(2026, 11, 6));
        verify(client).scheduleStrength(LocalDate.of(2026, 11, 7), LAST_NIGHT);
        assertThat(schedule.window().get("LA"))
                .containsExactly(OPENING_NIGHT, LocalDate.of(2026, 10, 20), LAST_NIGHT);
        assertThat(schedule.seasonGames()).containsEntry("LA", 3).containsEntry("TOR", 1);
    }

    @Test
    @DisplayName("once the season is under way, the window is the rest of it, tonight included")
    void underWayTheWindowIsTheRest() {
        LeagueSchedule schedule = on(LocalDate.of(2026, 10, 20)).schedule();

        assertThat(schedule.window().get("LA")).containsExactly(LocalDate.of(2026, 10, 20), LAST_NIGHT);
        assertThat(schedule.seasonGames()).as("still the whole season's").containsEntry("LA", 3);
    }

    @Test
    @DisplayName("after the last game, the window is the whole season again")
    void afterTheSeasonTheWholeSeason() {
        LeagueSchedule schedule = on(LocalDate.of(2027, 6, 1)).schedule();

        assertThat(schedule.window().get("LA")).hasSize(3);
    }

    @Test
    @DisplayName("keeps the season it read rather than reading it for every league")
    void keepsTheSeason() {
        SeasonScheduleService service = on(LocalDate.of(2026, 10, 20));
        service.schedule();
        service.schedule();

        verify(client, times(1)).scheduleWeeks();
    }

    @Test
    @DisplayName("says so when there is no schedule to set lineups against")
    void noScheduleIsAnError() {
        when(client.scheduleWeeks()).thenReturn(new ScheduleWeeksResponse().weeks(List.of()));

        assertThatThrownBy(() -> on(LocalDate.of(2026, 10, 20)).schedule())
                .isInstanceOf(IllegalStateException.class);
    }
}
