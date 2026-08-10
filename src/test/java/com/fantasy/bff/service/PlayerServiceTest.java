package com.fantasy.bff.service;

import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.espn.model.PlayerStatLine;
import com.fantasy.bff.service.mapping.EspnStatLineIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerServiceClient playerServiceClient;

    @Mock
    private EspnPlayerStatsProvider espnPlayerStats;

    @InjectMocks
    private PlayerService playerService;

    private static SkaterResponse mcDavid() {
        return new SkaterResponse(1, "Connor McDavid", "EDM",
                "https://assets.nhle.com/mugs/nhl/20242025/EDM/8478402.png", 97, Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(82, 1320),
                        new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1, 23, 38, 61, 8, 0,
                                348, 18.4, 812, 623, 42, 28, 0, 0, 108240)));
    }

    private static GoalieResponse shesterkin() {
        return new GoalieResponse(101, "Igor Shesterkin", "NYR",
                "https://assets.nhle.com/mugs/nhl/20242025/NYR/8478048.png", 31,
                new GoalieResponse.Stats(
                        new GoalieResponse.UtilityStats(58),
                        new GoalieResponse.ScoringStats(58, 36, 17, 0, 3, 1720, 1565, 155, 2.67, 0.910, 0.0, 0)));
    }

    @Test
    void getSkaters_returnsSkatersFromClient() {
        when(playerServiceClient.getSkaters()).thenReturn(List.of(mcDavid()));
        when(espnPlayerStats.index()).thenReturn(EspnStatLineIndex.empty());

        List<SkaterResponse> result = playerService.getSkaters();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Connor McDavid");
        assertThat(result.getFirst().positions()).contains(SkaterPosition.C);
    }

    @Test
    void getSkaters_fillsInTheStatsOnlyEspnReports() {
        when(playerServiceClient.getSkaters()).thenReturn(List.of(mcDavid()));
        when(espnPlayerStats.index()).thenReturn(new EspnStatLineIndex(List.of(
                new PlayerStatLine().id(3895074L).fullName("Connor McDavid").position("C")
                        .gamesPlayed(82).hatTricks(2).shifts(1413).timeOnIce(88566))));

        SkaterResponse.ScoringStats scoring = playerService.getSkaters().getFirst().stats().scoring();

        assertThat(scoring.hatTricks()).isEqualTo(2);
        assertThat(scoring.shifts()).isEqualTo(1413);
        assertThat(scoring.toi()).isEqualTo(88566);
        assertThat(scoring.goals()).isEqualTo(64);
    }

    @Test
    void getSkaters_keepsTheYahooLineWhenEspnHasNoMatch() {
        when(playerServiceClient.getSkaters()).thenReturn(List.of(mcDavid()));
        when(espnPlayerStats.index()).thenReturn(new EspnStatLineIndex(List.of(
                new PlayerStatLine().id(1L).fullName("Someone Else").position("D").hatTricks(9))));

        SkaterResponse.ScoringStats scoring = playerService.getSkaters().getFirst().stats().scoring();

        assertThat(scoring.hatTricks()).isZero();
        // The derived total time on ice survives when ESPN has nothing to replace it with.
        assertThat(scoring.toi()).isEqualTo(108240);
    }

    @Test
    void getSkaters_whenClientThrows_throwsIllegalStateException() {
        when(playerServiceClient.getSkaters()).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> playerService.getSkaters())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to retrieve skaters from player service");
    }

    @Test
    void getGoalies_returnsGoaliesFromClient() {
        when(playerServiceClient.getGoalies()).thenReturn(List.of(shesterkin()));
        when(espnPlayerStats.index()).thenReturn(EspnStatLineIndex.empty());

        List<GoalieResponse> result = playerService.getGoalies();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Igor Shesterkin");
    }

    @Test
    void getGoalies_fillsInOvertimeLossesAndDerivesWinPercentage() {
        when(playerServiceClient.getGoalies()).thenReturn(List.of(shesterkin()));
        when(espnPlayerStats.index()).thenReturn(new EspnStatLineIndex(List.of(
                new PlayerStatLine().id(1L).fullName("Igor Shesterkin").position("G")
                        .overtimeLosses(5).timeOnIce(209000))));

        GoalieResponse.ScoringStats scoring = playerService.getGoalies().getFirst().stats().scoring();

        assertThat(scoring.otl()).isEqualTo(5);
        assertThat(scoring.toi()).isEqualTo(209000);
        // 36 wins out of 58 decisions.
        assertThat(scoring.winPct()).isEqualTo(0.621);
    }

    @Test
    void getGoalies_whenClientThrows_throwsIllegalStateException() {
        when(playerServiceClient.getGoalies()).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> playerService.getGoalies())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to retrieve goalies from player service");
    }
}
