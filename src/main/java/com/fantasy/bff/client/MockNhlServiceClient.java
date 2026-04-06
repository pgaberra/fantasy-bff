package com.fantasy.bff.client;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@Profile("mock")
public class MockNhlServiceClient implements NhlServiceClient {

    @Override
    public List<SkaterResponse> getSkaters() {
        return List.of(
                new SkaterResponse(1, "Connor McDavid", Set.of(SkaterPosition.C),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(82, 1320),
                                new SkaterResponse.ScoringStats(64, 89, 33, 36, 22, 38, 1, 0, 8, 348, 18.4, 812, 623, 42, 28)
                        )),
                new SkaterResponse(2, "Nathan MacKinnon", Set.of(SkaterPosition.C),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(80, 1280),
                                new SkaterResponse.ScoringStats(51, 93, 28, 44, 18, 40, 0, 1, 6, 310, 16.5, 756, 580, 38, 32)
                        )),
                new SkaterResponse(3, "David Pastrnak", Set.of(SkaterPosition.RW),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(81, 1100),
                                new SkaterResponse.ScoringStats(60, 52, 15, 28, 24, 20, 0, 0, 10, 380, 15.8, 0, 0, 55, 18)
                        )),
                new SkaterResponse(4, "Nikita Kucherov", Set.of(SkaterPosition.RW),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(79, 1190),
                                new SkaterResponse.ScoringStats(44, 100, 22, 40, 16, 42, 0, 0, 7, 290, 15.2, 0, 0, 30, 22)
                        )),
                new SkaterResponse(5, "Cale Makar", Set.of(SkaterPosition.D),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(77, 1350),
                                new SkaterResponse.ScoringStats(28, 72, 30, 20, 12, 30, 1, 0, 5, 260, 10.8, 0, 0, 48, 120)
                        )),
                new SkaterResponse(6, "Mitch Marner", Set.of(SkaterPosition.RW),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(82, 1140),
                                new SkaterResponse.ScoringStats(36, 81, 12, 18, 14, 36, 0, 0, 4, 220, 16.4, 0, 0, 26, 34)
                        )),
                new SkaterResponse(7, "Auston Matthews", Set.of(SkaterPosition.C),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(81, 1080),
                                new SkaterResponse.ScoringStats(69, 54, 18, 24, 26, 18, 0, 0, 11, 420, 16.4, 580, 440, 22, 14)
                        )),
                new SkaterResponse(8, "Leon Draisaitl", Set.of(SkaterPosition.C, SkaterPosition.LW),
                        new SkaterResponse.Stats(
                                new SkaterResponse.UtilityStats(80, 1260),
                                new SkaterResponse.ScoringStats(52, 84, 20, 50, 20, 36, 0, 0, 9, 300, 17.3, 640, 510, 34, 20)
                        ))
        );
    }

    @Override
    public List<GoalieResponse> getGoalies() {
        return List.of(
                new GoalieResponse(101, "Igor Shesterkin",
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(58),
                                new GoalieResponse.ScoringStats(58, 36, 17, 3, 1720, 1565, 155, 2.67, 0.910)
                        )),
                new GoalieResponse(102, "Andrei Vasilevskiy",
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(60),
                                new GoalieResponse.ScoringStats(60, 37, 18, 4, 1780, 1624, 156, 2.60, 0.912)
                        )),
                new GoalieResponse(103, "Connor Hellebuyck",
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(62),
                                new GoalieResponse.ScoringStats(62, 37, 20, 5, 1900, 1738, 162, 2.62, 0.915)
                        )),
                new GoalieResponse(104, "Linus Ullmark",
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(49),
                                new GoalieResponse.ScoringStats(49, 33, 10, 6, 1380, 1270, 110, 2.24, 0.920)
                        )),
                new GoalieResponse(105, "Jake Oettinger",
                        new GoalieResponse.Stats(
                                new GoalieResponse.UtilityStats(55),
                                new GoalieResponse.ScoringStats(55, 30, 19, 2, 1620, 1474, 146, 2.65, 0.909)
                        ))
        );
    }
}
