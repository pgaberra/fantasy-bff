package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerPoolSource playerPool;

    @InjectMocks
    private PlayerService playerService;

    private static SkaterResponse mcDavid() {
        return new SkaterResponse(1, "Connor McDavid", "EDM", "/players/1/headshot", 97,
                Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(82, 1320),
                        new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1,
                                23, 38, 61, 8, 0, 348, 18.4, 812, 623, 42, 28, 0, 0, 108240)));
    }

    private static GoalieResponse shesterkin() {
        return new GoalieResponse(101, "Igor Shesterkin", "NYR", "/players/101/headshot", 31,
                new GoalieResponse.Stats(
                        new GoalieResponse.UtilityStats(58),
                        new GoalieResponse.ScoringStats(58, 36, 17, 5, 3, 1720, 1565, 155, 2.67,
                                0.910, 0.621, 209000)));
    }

    @Test
    void getSkaters_servesWhateverTheConfiguredSourceHolds() {
        when(playerPool.getSkaters()).thenReturn(List.of(mcDavid()));

        List<SkaterResponse> result = playerService.getSkaters();

        assertThat(result).singleElement()
                .satisfies(skater -> assertThat(skater.name()).isEqualTo("Connor McDavid"));
    }

    @Test
    void getGoalies_servesWhateverTheConfiguredSourceHolds() {
        when(playerPool.getGoalies()).thenReturn(List.of(shesterkin()));

        assertThat(playerService.getGoalies()).singleElement()
                .satisfies(goalie -> assertThat(goalie.name()).isEqualTo("Igor Shesterkin"));
    }

    private static SkaterResponse skater(int id, String name, int points) {
        return new SkaterResponse(id, name, "EDM", "/players/" + id + "/headshot", 97,
                Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(82, 1320),
                        new SkaterResponse.ScoringStats(0, 0, points, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0.0, 0, 0, 0, 0, 0, 0, 0)));
    }

    private static GoalieResponse goalie(int id, String name, int wins, int saves) {
        return new GoalieResponse(id, name, "NYR", "/players/" + id + "/headshot", 31,
                new GoalieResponse.Stats(
                        new GoalieResponse.UtilityStats(58),
                        new GoalieResponse.ScoringStats(0, wins, 0, 0, 0, 0, saves, 0, 0.0,
                                0.0, 0.0, 0)));
    }

    @Test
    void getSkaters_servesTheHighestScoringFirst() {
        when(playerPool.getSkaters()).thenReturn(List.of(
                skater(1, "Middle", 80), skater(2, "Best", 120), skater(3, "Worst", 40)));

        assertThat(playerService.getSkaters()).extracting(SkaterResponse::name)
                .containsExactly("Best", "Middle", "Worst");
    }

    // The order is what makes the limit meaningful: five rows of a preview want the five the
    // board opens with, not five arbitrary players.
    @Test
    void getSkaters_withALimit_servesThatManyFromTheTop() {
        when(playerPool.getSkaters()).thenReturn(List.of(
                skater(1, "Middle", 80), skater(2, "Best", 120), skater(3, "Worst", 40)));

        assertThat(playerService.getSkaters(2)).extracting(SkaterResponse::name)
                .containsExactly("Best", "Middle");
    }

    @Test
    void getSkaters_withALimitLargerThanThePool_servesThePool() {
        when(playerPool.getSkaters()).thenReturn(List.of(skater(1, "Only", 80)));

        assertThat(playerService.getSkaters(50)).hasSize(1);
    }

    @Test
    void getGoalies_servesTheMostWinsFirstAndHonoursTheLimit() {
        when(playerPool.getGoalies()).thenReturn(List.of(
                goalie(1, "Fewest", 12, 900), goalie(2, "Most", 39, 1300),
                goalie(3, "Middle", 30, 1400)));

        assertThat(playerService.getGoalies(2)).extracting(GoalieResponse::name)
                .containsExactly("Most", "Middle");
    }

    // Two goalies on the same wins are separated by saves, and never by chance: the same request
    // has to answer the same way twice, or a cached page and a fresh one disagree.
    @Test
    void getGoalies_breaksTiesOnSaves() {
        when(playerPool.getGoalies()).thenReturn(List.of(
                goalie(1, "Fewer saves", 30, 1200), goalie(2, "More saves", 30, 1400)));

        assertThat(playerService.getGoalies(null)).extracting(GoalieResponse::name)
                .containsExactly("More saves", "Fewer saves");
    }

    @Test
    void getHeadshot_passesThroughWhatTheSourceHas() {
        when(playerPool.getHeadshot(1)).thenReturn(Optional.of(new byte[] {1, 2, 3}));

        assertThat(playerService.getHeadshot(1)).contains(new byte[] {1, 2, 3});
    }

    @Test
    void getSkaters_whenTheSourceThrows_throwsIllegalStateException() {
        when(playerPool.getSkaters()).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> playerService.getSkaters())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to retrieve skaters from player service");
    }

    @Test
    void getGoalies_whenTheSourceThrows_throwsIllegalStateException() {
        when(playerPool.getGoalies()).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> playerService.getGoalies())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to retrieve goalies from player service");
    }

    @Test
    void getHeadshot_whenTheSourceThrows_throwsIllegalStateException() {
        when(playerPool.getHeadshot(1)).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> playerService.getHeadshot(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to retrieve a headshot from player service");
    }
}
