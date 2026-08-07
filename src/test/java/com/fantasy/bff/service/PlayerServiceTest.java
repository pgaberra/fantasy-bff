package com.fantasy.bff.service;

import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
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

    @InjectMocks
    private PlayerService playerService;

    @Test
    void getSkaters_returnsSkatersFromClient() {
        List<SkaterResponse> expected = List.of(
                new SkaterResponse(1, "Connor McDavid", "EDM",
                        "https://assets.nhle.com/mugs/nhl/20242025/EDM/8478402.png", 97, Set.of(SkaterPosition.C),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(82, 1320),
                                new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1, 8, 348, 18.4, 812, 623, 42, 28)
                        ))
        );
        when(playerServiceClient.getSkaters()).thenReturn(expected);

        List<SkaterResponse> result = playerService.getSkaters();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Connor McDavid");
        assertThat(result.getFirst().positions()).contains(SkaterPosition.C);
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
        List<GoalieResponse> expected = List.of(
                new GoalieResponse(101, "Igor Shesterkin", "NYR",
                        "https://assets.nhle.com/mugs/nhl/20242025/NYR/8478048.png", 31,
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(58),
                                new GoalieResponse.ScoringStats(58, 36, 17, 3, 1720, 1565, 155, 2.67, 0.910)
                        ))
        );
        when(playerServiceClient.getGoalies()).thenReturn(expected);

        List<GoalieResponse> result = playerService.getGoalies();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Igor Shesterkin");
    }

    @Test
    void getGoalies_whenClientThrows_throwsIllegalStateException() {
        when(playerServiceClient.getGoalies()).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> playerService.getGoalies())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to retrieve goalies from player service");
    }
}
