package com.fantasy.bff.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.generated.projection.model.NightResponse;
import com.fantasy.bff.generated.projection.model.RatedGameResponse;
import com.fantasy.bff.generated.projection.model.ScheduleStrengthResponse;
import com.fantasy.bff.generated.projection.model.ScheduleWeekResponse;
import com.fantasy.bff.generated.projection.model.ScheduleWeeksResponse;
import com.fantasy.bff.generated.projection.model.TeamScheduleResponse;
import com.fantasy.bff.security.JwtTokenValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** With STREAMER_PLANNER_ENABLED=true, any signed-in user gets the weeks and the rated teams. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "streamer-planner.enabled=true")
class StreamerPlannerControllerIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    @MockitoBean private ProjectionServiceClient projectionServiceClient;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtTokenValidator.generateToken("user-1", "a@example.com");
    }

    @Test
    void theFeatureIsReportedOn() throws Exception {
        mockMvc.perform(get("/api/v1/features")).andExpect(jsonPath("$.streamerPlanner").value(true));
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/streamer-planner/weeks")).andExpect(status().isUnauthorized());
        verifyNoInteractions(projectionServiceClient);
    }

    @Test
    void weeksAreServed() throws Exception {
        ScheduleWeekResponse week = new ScheduleWeekResponse();
        week.setWeek(2);
        week.setStart(MONDAY);
        week.setEnd(MONDAY.plusDays(6));
        week.setGames(52);
        ScheduleWeeksResponse answer = new ScheduleWeeksResponse();
        answer.setSeason(2026);
        answer.setCurrentWeek(2);
        answer.setWeeks(List.of(week));
        when(projectionServiceClient.scheduleWeeks()).thenReturn(answer);

        mockMvc.perform(get("/api/v1/streamer-planner/weeks").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value(2026))
                .andExpect(jsonPath("$.currentWeek").value(2))
                .andExpect(jsonPath("$.weeks[0].start").value("2026-10-12"))
                .andExpect(jsonPath("$.weeks[0].end").value("2026-10-18"))
                .andExpect(jsonPath("$.weeks[0].games").value(52));
    }

    @Test
    void teamsAreServedRatedWithTheirGames() throws Exception {
        RatedGameResponse game = new RatedGameResponse();
        game.setDate(MONDAY);
        game.setOpponent("SJS");
        game.setHome(true);
        game.setOffNight(true);
        game.setBackToBack(false);
        game.setOpponentGoalsAgainst(new BigDecimal("1.11"));
        game.setOpponentGoalsFor(new BigDecimal("0.9"));
        TeamScheduleResponse team = new TeamScheduleResponse();
        team.setTeam("EDM");
        team.setGames(1);
        team.setOffNightGames(1);
        team.setBackToBacks(0);
        team.setHomeGames(1);
        team.setSkaterScore(new BigDecimal("1.39"));
        team.setSkaterRank(1);
        team.setGoalieScore(new BigDecimal("1.39"));
        team.setGoalieRank(1);
        team.setSchedule(List.of(game));
        NightResponse night = new NightResponse();
        night.setDate(MONDAY);
        night.setGames(3);
        night.setOffNight(true);
        ScheduleStrengthResponse answer = new ScheduleStrengthResponse();
        answer.setSeason(2026);
        answer.setStart(MONDAY);
        answer.setEnd(MONDAY.plusDays(6));
        answer.setOffNightMaxGames(7);
        answer.setNights(List.of(night));
        answer.setTeams(List.of(team));
        when(projectionServiceClient.scheduleStrength(MONDAY, MONDAY.plusDays(6))).thenReturn(answer);

        mockMvc.perform(get("/api/v1/streamer-planner/teams?start=2026-10-12&end=2026-10-18")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offNightMaxGames").value(7))
                .andExpect(jsonPath("$.nights[0].offNight").value(true))
                .andExpect(jsonPath("$.teams[0].team").value("EDM"))
                .andExpect(jsonPath("$.teams[0].skaterScore").value(1.39))
                .andExpect(jsonPath("$.teams[0].schedule[0].opponent").value("SJS"))
                .andExpect(jsonPath("$.teams[0].schedule[0].opponentGoalsAgainst").value(1.11));
    }

    @Test
    void aBackwardsOrOverlongStretchIsRefusedBeforeProjectionServiceIsAsked() throws Exception {
        mockMvc.perform(get("/api/v1/streamer-planner/teams?start=2026-10-18&end=2026-10-12")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/streamer-planner/teams?start=2026-10-01&end=2026-11-01")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(projectionServiceClient);
    }

    @Test
    void aMissingOrMalformedDateIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/streamer-planner/teams?start=2026-10-12").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/streamer-planner/teams?start=next-monday&end=2026-10-18")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(projectionServiceClient);
    }
}
