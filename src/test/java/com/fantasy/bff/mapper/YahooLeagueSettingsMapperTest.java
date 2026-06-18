package com.fantasy.bff.mapper;

import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.RosterSlot;
import com.fantasy.bff.generated.yahoo.model.StatCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YahooLeagueSettingsMapperTest {

    private static StatCategory cat(int statId, String name) {
        return new StatCategory().statId(statId).name(name).displayName(name);
    }

    private static StatCategory cat(int statId, String name, double pointValue) {
        return new StatCategory().statId(statId).name(name).displayName(name).pointValue(pointValue);
    }

    private static RosterSlot slot(String position, int count) {
        return new RosterSlot().position(position).count(count);
    }

    private static LeagueSettingsResponse league(String scoringType,
                                                 List<StatCategory> stats,
                                                 List<RosterSlot> roster) {
        return new LeagueSettingsResponse()
                .leagueKey("453.l.1")
                .name("Test League")
                .scoringType(scoringType)
                .statCategories(stats)
                .rosterPositions(roster);
    }

    @Test
    void mapsHeadToHeadPointsLeagueDerivingWeightsAndZeroingTheRest() {
        LeagueProjectionSettingsResponse mapped = YahooLeagueSettingsMapper.toProjectionSettings(
                league("headpoint",
                        List.of(cat(1, "Goals", 3), cat(2, "Assists", 2), cat(25, "Saves", 0.2)),
                        List.of(slot("C", 2), slot("G", 2), slot("BN", 4))),
                12);

        assertThat(mapped.scoringType()).isEqualTo("points");
        assertThat(mapped.activeScoringColumns()).containsExactly("goals", "assists", "sv");
        assertThat(mapped.statWeights()).containsEntry("goals", 3.0).containsEntry("assists", 2.0)
                .containsEntry("sv", 0.2).containsEntry("hits", 0.0);
        assertThat(mapped.leagueSize()).isEqualTo(12);
        assertThat(mapped.rosterSlots().getC()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getG()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getBn()).isEqualTo(4);
        assertThat(mapped.rosterSlots().getD()).isZero();
    }

    @Test
    void mapsHeadToHeadCategoriesLeagueWithNullWeights() {
        LeagueProjectionSettingsResponse mapped = YahooLeagueSettingsMapper.toProjectionSettings(
                league("head",
                        List.of(cat(1, "Goals"), cat(2, "Assists"), cat(19, "Wins"), cat(26, "Save %")),
                        List.of()),
                10);

        assertThat(mapped.scoringType()).isEqualTo("category");
        assertThat(mapped.statWeights()).isNull();
        assertThat(mapped.activeScoringColumns()).containsExactly("goals", "assists", "w", "svPct");
        assertThat(mapped.leagueSize()).isEqualTo(10);
    }

    @Test
    void mapsRotisserieLeagueAsCategory() {
        LeagueProjectionSettingsResponse mapped = YahooLeagueSettingsMapper.toProjectionSettings(
                league("roto", List.of(cat(1, "Goals"), cat(2, "Assists")), List.of()), null);

        assertThat(mapped.scoringType()).isEqualTo("category");
        assertThat(mapped.statWeights()).isNull();
    }

    @Test
    void mapsSeasonLongPointsLeagueAsPoints() {
        LeagueProjectionSettingsResponse mapped = YahooLeagueSettingsMapper.toProjectionSettings(
                league("point", List.of(cat(1, "Goals", 1.5)), List.of()), null);

        assertThat(mapped.scoringType()).isEqualTo("points");
        assertThat(mapped.statWeights()).containsEntry("goals", 1.5);
    }

    @Test
    void fallsBackToPointValuePresenceForUnrecognisedScoringType() {
        LeagueProjectionSettingsResponse withWeights = YahooLeagueSettingsMapper.toProjectionSettings(
                league("mystery", List.of(cat(1, "Goals", 2)), List.of()), null);
        assertThat(withWeights.scoringType()).isEqualTo("points");

        LeagueProjectionSettingsResponse withoutWeights = YahooLeagueSettingsMapper.toProjectionSettings(
                league("mystery", List.of(cat(1, "Goals")), List.of()), null);
        assertThat(withoutWeights.scoringType()).isEqualTo("category");
    }

    @Test
    void flagsStatsWithNoProjectionEquivalentAsUnsupported() {
        LeagueProjectionSettingsResponse mapped = YahooLeagueSettingsMapper.toProjectionSettings(
                league("head", List.of(cat(1, "Goals"), cat(13, "Game-Tying Goals")), List.of()), null);

        assertThat(mapped.activeScoringColumns()).containsExactly("goals");
        assertThat(mapped.unsupportedStats()).containsExactly("Game-Tying Goals");
    }

    @Test
    void routesGpAndToiCategoriesToUtilityAndAlwaysKeepsGp() {
        LeagueProjectionSettingsResponse withToi = YahooLeagueSettingsMapper.toProjectionSettings(
                league("head", List.of(cat(34, "Time on Ice/G"), cat(1, "Goals")), List.of()), null);
        assertThat(withToi.activeUtilityColumns()).containsExactly("gp", "toiPerGame");

        LeagueProjectionSettingsResponse noUtil = YahooLeagueSettingsMapper.toProjectionSettings(
                league("head", List.of(cat(1, "Goals")), List.of()), null);
        assertThat(noUtil.activeUtilityColumns()).containsExactly("gp");
    }

    @Test
    void mapsRosterSlotsIgnoresIrAndApproximatesWingToUtil() {
        LeagueProjectionSettingsResponse mapped = YahooLeagueSettingsMapper.toProjectionSettings(
                league("head", List.of(),
                        List.of(slot("C", 2), slot("LW", 2), slot("RW", 2), slot("D", 4),
                                slot("Util", 1), slot("G", 2), slot("BN", 4), slot("IR", 2), slot("W", 1))),
                null);

        assertThat(mapped.rosterSlots().getC()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getLw()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getRw()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getD()).isEqualTo(4);
        assertThat(mapped.rosterSlots().getUtil()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getBn()).isEqualTo(4);
        assertThat(mapped.rosterSlots().getG()).isEqualTo(2);
        assertThat(mapped.unsupportedRosterCodes()).contains("IR", "W");
    }

    @Test
    void clampsLeagueSizeAndLeavesItNullWhenUnknown() {
        assertThat(YahooLeagueSettingsMapper.toProjectionSettings(league("head", List.of(), List.of()), null)
                .leagueSize()).isNull();
        assertThat(YahooLeagueSettingsMapper.toProjectionSettings(league("head", List.of(), List.of()), 40)
                .leagueSize()).isEqualTo(30);
        assertThat(YahooLeagueSettingsMapper.toProjectionSettings(league("head", List.of(), List.of()), 1)
                .leagueSize()).isEqualTo(2);
        assertThat(YahooLeagueSettingsMapper.toProjectionSettings(league("head", List.of(), List.of()), 14)
                .leagueSize()).isEqualTo(14);
    }
}
