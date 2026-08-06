package com.fantasy.bff.mapper;

import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.dto.response.ScoringBasis;
import com.fantasy.bff.generated.espn.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.espn.model.RosterSlot;
import com.fantasy.bff.generated.espn.model.StatCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EspnLeagueSettingsMapperTest {

    private final EspnLeagueSettingsMapper mapper = new EspnLeagueSettingsMapper();

    private static StatCategory cat(int statId, String name) {
        return new StatCategory().statId(statId).name(name);
    }

    private static StatCategory cat(int statId, String name, double pointValue) {
        return new StatCategory().statId(statId).name(name).pointValue(pointValue);
    }

    private static RosterSlot slot(String position, int count) {
        return new RosterSlot().position(position).count(count);
    }

    private static LeagueSettingsResponse league(String scoringType, Integer size,
                                                 List<StatCategory> stats, List<RosterSlot> roster) {
        return new LeagueSettingsResponse()
                .leagueId("123")
                .name("Test League")
                .scoringType(scoringType)
                .size(size)
                .statCategories(stats)
                .rosterPositions(roster);
    }

    @Test
    void mapsHeadToHeadPointsLeagueDerivingWeightsAndZeroingTheRest() {
        LeagueProjectionSettingsResponse mapped = mapper.toProjectionSettings(
                league("H2H_POINTS", 12,
                        List.of(cat(13, "G", 6), cat(14, "A", 4), cat(6, "SV", 0.2)),
                        List.of(slot("C", 2), slot("G", 2), slot("BN", 4))));

        assertThat(mapped.scoringType()).isEqualTo(ScoringBasis.POINTS);
        assertThat(mapped.activeScoringColumns()).containsExactly("goals", "assists", "sv");
        assertThat(mapped.statWeights()).containsEntry("goals", 6.0).containsEntry("assists", 4.0)
                .containsEntry("sv", 0.2).containsEntry("hits", 0.0);
        assertThat(mapped.leagueSize()).isEqualTo(12);
        assertThat(mapped.rosterSlots().getC()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getG()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getBn()).isEqualTo(4);
        assertThat(mapped.rosterSlots().getD()).isZero();
    }

    @Test
    void mapsHeadToHeadCategoriesLeagueWithNullWeights() {
        LeagueProjectionSettingsResponse mapped = mapper.toProjectionSettings(
                league("H2H_CATEGORY", 10,
                        List.of(cat(13, "G"), cat(14, "A"), cat(1, "W"), cat(11, "SV%")),
                        List.of()));

        assertThat(mapped.scoringType()).isEqualTo(ScoringBasis.CATEGORY);
        assertThat(mapped.statWeights()).isNull();
        assertThat(mapped.activeScoringColumns()).containsExactly("goals", "assists", "w", "svPct");
        assertThat(mapped.leagueSize()).isEqualTo(10);
    }

    @Test
    void mapsRotisserieLeagueAsCategory() {
        LeagueProjectionSettingsResponse mapped = mapper.toProjectionSettings(
                league("ROTO", null, List.of(cat(13, "G"), cat(14, "A")), List.of()));

        assertThat(mapped.scoringType()).isEqualTo(ScoringBasis.CATEGORY);
        assertThat(mapped.statWeights()).isNull();
    }

    @Test
    void mapsTotalPointsLeagueAsPoints() {
        LeagueProjectionSettingsResponse mapped = mapper.toProjectionSettings(
                league("TOTAL_POINTS", null, List.of(cat(13, "G", 1.5)), List.of()));

        assertThat(mapped.scoringType()).isEqualTo(ScoringBasis.POINTS);
        assertThat(mapped.statWeights()).containsEntry("goals", 1.5);
    }

    @Test
    void fallsBackToPointValuePresenceForUnrecognisedScoringType() {
        LeagueProjectionSettingsResponse withWeights = mapper.toProjectionSettings(
                league("MYSTERY", null, List.of(cat(13, "G", 2)), List.of()));
        assertThat(withWeights.scoringType()).isEqualTo(ScoringBasis.POINTS);

        LeagueProjectionSettingsResponse withoutWeights = mapper.toProjectionSettings(
                league("MYSTERY", null, List.of(cat(13, "G")), List.of()));
        assertThat(withoutWeights.scoringType()).isEqualTo(ScoringBasis.CATEGORY);
    }

    @Test
    void flagsStatsWithNoProjectionEquivalentAsUnsupported() {
        LeagueProjectionSettingsResponse mapped = mapper.toProjectionSettings(
                league("H2H_CATEGORY", null, List.of(cat(13, "G"), cat(99, "Defensive Points")), List.of()));

        assertThat(mapped.activeScoringColumns()).containsExactly("goals");
        assertThat(mapped.unsupportedStats()).containsExactly("Defensive Points");
    }

    @Test
    void routesGpAndToiToUtilityAndAlwaysKeepsGp() {
        LeagueProjectionSettingsResponse withToi = mapper.toProjectionSettings(
                league("H2H_CATEGORY", null, List.of(cat(27, "ATOI"), cat(13, "G")), List.of()));
        assertThat(withToi.activeUtilityColumns()).containsExactly("gp", "toiPerGame");

        LeagueProjectionSettingsResponse noUtil = mapper.toProjectionSettings(
                league("H2H_CATEGORY", null, List.of(cat(13, "G")), List.of()));
        assertThat(noUtil.activeUtilityColumns()).containsExactly("gp");
    }

    @Test
    void mapsRosterSlotsIgnoresIrAndApproximatesForwardToUtil() {
        LeagueProjectionSettingsResponse mapped = mapper.toProjectionSettings(
                league("H2H_CATEGORY", null, List.of(),
                        List.of(slot("C", 2), slot("LW", 2), slot("RW", 2), slot("D", 4),
                                slot("Util", 1), slot("G", 2), slot("BN", 4), slot("IR", 2), slot("F", 1))));

        assertThat(mapped.rosterSlots().getC()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getLw()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getRw()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getD()).isEqualTo(4);
        assertThat(mapped.rosterSlots().getUtil()).isEqualTo(2);
        assertThat(mapped.rosterSlots().getBn()).isEqualTo(4);
        assertThat(mapped.rosterSlots().getG()).isEqualTo(2);
        assertThat(mapped.unsupportedRosterCodes()).contains("IR", "F");
    }

    @Test
    void clampsLeagueSizeAndLeavesItNullWhenUnknown() {
        assertThat(mapper.toProjectionSettings(league("H2H_CATEGORY", null, List.of(), List.of()))
                .leagueSize()).isNull();
        assertThat(mapper.toProjectionSettings(league("H2H_CATEGORY", 40, List.of(), List.of()))
                .leagueSize()).isEqualTo(30);
        assertThat(mapper.toProjectionSettings(league("H2H_CATEGORY", 1, List.of(), List.of()))
                .leagueSize()).isEqualTo(2);
        assertThat(mapper.toProjectionSettings(league("H2H_CATEGORY", 14, List.of(), List.of()))
                .leagueSize()).isEqualTo(14);
    }
}
