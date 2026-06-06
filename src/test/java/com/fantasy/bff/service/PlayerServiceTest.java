package com.fantasy.bff.service;

import com.fantasy.bff.client.NhlServiceClient;
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
    private NhlServiceClient nhlServiceClient;

    @InjectMocks
    private PlayerService playerService;

    @Test
    void getSkaters_returnsSkatersFromClient() {
        List<SkaterResponse> expected = List.of(
                new SkaterResponse(1, "Connor McDavid", Set.of(SkaterPosition.C),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(82, 1320),
                                new SkaterResponse.ScoringStats(64, 89, 33, 36, 22, 38, 1, 0, 8, 348, 18.4, 812, 623, 42, 28)
                        ))
        );
        when(nhlServiceClient.getSkaters()).thenReturn(expected);

        List<SkaterResponse> result = playerService.getSkaters();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Connor McDavid");
        assertThat(result.getFirst().positions()).contains(SkaterPosition.C);
    }

    @Test
    void getSkaters_whenClientThrows_throwsIllegalStateException() {
        when(nhlServiceClient.getSkaters()).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> playerService.getSkaters())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to retrieve skaters from NHL service");
    }

    @Test
    void getGoalies_returnsGoaliesFromClient() {
        List<GoalieResponse> expected = List.of(
                new GoalieResponse(101, "Igor Shesterkin",
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(58),
                                new GoalieResponse.ScoringStats(58, 36, 17, 3, 1720, 1565, 155, 2.67, 0.910)
                        ))
        );
        when(nhlServiceClient.getGoalies()).thenReturn(expected);

        List<GoalieResponse> result = playerService.getGoalies();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Igor Shesterkin");
    }

    @Test
    void getGoalies_whenClientThrows_throwsIllegalStateException() {
        when(nhlServiceClient.getGoalies()).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> playerService.getGoalies())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to retrieve goalies from NHL service");
    }
}
