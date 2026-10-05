package com.fantasy.bff.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasy.bff.dto.response.DraftAnalysisPick;
import com.fantasy.bff.dto.response.DraftAnalysisTeam;
import com.fantasy.bff.dto.response.DraftPickGrade;
import com.fantasy.bff.dto.response.LeagueDraftPick;
import com.fantasy.bff.dto.response.LeagueDraftTeam;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DraftGraderTest {

    private static final List<LeagueDraftTeam> TEAMS = List.of(
            new LeagueDraftTeam("t1", "Mine", true),
            new LeagueDraftTeam("t2", "Jocke", false));

    /** Players 1..n, valued n down to 1, so player k is the model's rank k. */
    private static Map<Integer, Double> values(int count) {
        Map<Integer, Double> values = new HashMap<>();
        for (int id = 1; id <= count; id++) {
            values.put(id, (double) (count - id + 1));
        }
        return values;
    }

    /** The same players valued with no positional adjustment: value over replacement is the value. */
    private static Map<Integer, ReplacementLevel.Valued> valued(int count) {
        Map<Integer, ReplacementLevel.Valued> valued = new HashMap<>();
        values(count).forEach((id, value) -> valued.put(id, new ReplacementLevel.Valued(value, value, "C" + id)));
        return valued;
    }

    private static Map<Integer, DraftGrader.Player> directory(int count) {
        Map<Integer, DraftGrader.Player> directory = new HashMap<>();
        for (int id = 1; id <= count; id++) {
            directory.put(id, new DraftGrader.Player("Player " + id, "TOR", Set.of("C")));
        }
        return directory;
    }

    @Test
    void takingTheModelsPlayerAtEveryPickIsFairThroughout() {
        List<LeagueDraftPick> picks = List.of(
                new LeagueDraftPick(1, 1, "t1", 1),
                new LeagueDraftPick(2, 1, "t2", 2),
                new LeagueDraftPick(3, 2, "t2", 3),
                new LeagueDraftPick(4, 2, "t1", 4));

        DraftGrader.Graded graded = DraftGrader.grade(valued(10), directory(10), picks, TEAMS, 2, false);

        assertThat(graded.picks()).extracting(DraftAnalysisPick::grade).containsOnly(DraftPickGrade.FAIR);
        assertThat(graded.picks()).extracting(DraftAnalysisPick::valueOverSlot).containsOnly(0.0);
        assertThat(graded.picks()).extracting(DraftAnalysisPick::bestAvailable).containsOnlyNulls();
        assertThat(graded.picks()).extracting(DraftAnalysisPick::aiRank).containsExactly(1, 2, 3, 4);
        assertThat(graded.teams()).extracting(DraftAnalysisTeam::grade).containsOnly("C");
    }

    @Test
    void aReachNamesTheModelsBestPlayerStillOnTheBoard() {
        List<LeagueDraftPick> picks = List.of(
                new LeagueDraftPick(1, 1, "t1", 1),
                new LeagueDraftPick(2, 1, "t2", 30));

        DraftAnalysisPick reach = DraftGrader.grade(valued(40), directory(40), picks, TEAMS, 2, false)
                .picks().get(1);

        assertThat(reach.grade()).isEqualTo(DraftPickGrade.BIG_REACH);
        assertThat(reach.aiRank()).isEqualTo(30);
        assertThat(reach.valueOverSlot()).isCloseTo(11.0 - 39.0, within(1e-9));
        assertThat(reach.bestAvailable()).isNotNull();
        assertThat(reach.bestAvailable().playerId()).isEqualTo(2);
        assertThat(reach.bestAvailable().aiRank()).isEqualTo(2);
        assertThat(reach.bestAvailable().name()).isEqualTo("Player 2");
        assertThat(reach.bestAvailable().positionRank()).isEqualTo("C2");
        assertThat(reach.positionRank()).isEqualTo("C30");
    }

    @Test
    void aPlayerTakenWellAfterHisRankIsASteal() {
        List<LeagueDraftPick> picks = new java.util.ArrayList<>();
        for (int overall = 1; overall <= 59; overall++) {
            picks.add(new LeagueDraftPick(overall, (overall + 11) / 12, overall % 2 == 0 ? "t2" : "t1", overall + 100));
        }
        picks.add(new LeagueDraftPick(60, 5, "t1", 30));
        Map<Integer, ReplacementLevel.Valued> values = valued(30);
        Map<Integer, DraftGrader.Player> directory = directory(30);

        DraftAnalysisPick steal = DraftGrader.grade(values, directory, picks, TEAMS, 12, false).picks().get(59);

        assertThat(steal.grade()).isEqualTo(DraftPickGrade.STEAL);
        assertThat(steal.bestAvailable().playerId()).as("the model's top player went undrafted").isEqualTo(1);
    }

    @Test
    void aPlayerTheModelDoesNotProjectIsUnrankedAndAddsNothing() {
        List<LeagueDraftPick> picks = List.of(new LeagueDraftPick(1, 1, "t1", 99));
        Map<Integer, DraftGrader.Player> directory = directory(5);
        directory.put(99, new DraftGrader.Player("Overseas Signing", null, Set.of("D")));

        DraftGrader.Graded graded = DraftGrader.grade(valued(5), directory, picks, TEAMS, 2, false);
        DraftAnalysisPick pick = graded.picks().getFirst();

        assertThat(pick.grade()).isEqualTo(DraftPickGrade.UNRANKED);
        assertThat(pick.name()).isEqualTo("Overseas Signing");
        assertThat(pick.positions()).containsExactly("D");
        assertThat(pick.aiRank()).isNull();
        assertThat(pick.positionRank()).isNull();
        assertThat(pick.valueOverSlot()).isNull();
        assertThat(pick.bestAvailable().playerId()).isEqualTo(1);
        DraftAnalysisTeam mine = graded.teams().stream().filter(DraftAnalysisTeam::mine).findFirst().orElseThrow();
        assertThat(mine.picks()).isEqualTo(1);
        assertThat(mine.valueAdded()).isZero();
        assertThat(mine.grade()).as("nothing it picked could be graded").isNull();
    }

    @Test
    void anAuctionIsNotGraded() {
        List<LeagueDraftPick> picks = List.of(new LeagueDraftPick(1, 1, "t1", 9));

        DraftAnalysisPick pick = DraftGrader.grade(valued(10), directory(10), picks, TEAMS, 2, true).picks().getFirst();

        assertThat(pick.grade()).isNull();
        assertThat(pick.aiRank()).isEqualTo(9);
    }

    @Test
    void theTeamsComeBestDraftFirst() {
        List<LeagueDraftPick> picks = List.of(
                new LeagueDraftPick(1, 1, "t1", 8),
                new LeagueDraftPick(2, 1, "t2", 1));

        List<DraftAnalysisTeam> teams = DraftGrader.grade(valued(10), directory(10), picks, TEAMS, 2, false).teams();

        assertThat(teams).extracting(DraftAnalysisTeam::name).containsExactly("Jocke", "Mine");
        assertThat(teams.get(0).goodPicks()).isEqualTo(1);
        assertThat(teams.get(0).grade()).isEqualTo("A");
        assertThat(teams.get(1).badPicks()).isEqualTo(1);
        assertThat(teams.get(1).grade()).isEqualTo("F");
    }

    @Test
    void theSlackKeepsTheTopOfADraftFair() {
        assertThat(DraftGrader.grade(1, 2, 12)).isEqualTo(DraftPickGrade.FAIR);
        assertThat(DraftGrader.grade(1, 12, 12)).isEqualTo(DraftPickGrade.BIG_REACH);
        assertThat(DraftGrader.grade(50, 35, 12)).isEqualTo(DraftPickGrade.GOOD);
        assertThat(DraftGrader.grade(150, 190, 12)).isEqualTo(DraftPickGrade.REACH);
        assertThat(DraftGrader.grade(150, 250, 12)).isEqualTo(DraftPickGrade.BIG_REACH);
    }
}
