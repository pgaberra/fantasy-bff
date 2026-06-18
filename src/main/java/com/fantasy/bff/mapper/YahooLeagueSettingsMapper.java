package com.fantasy.bff.mapper;

import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.dto.response.ScoringBasis;
import com.fantasy.bff.generated.db.model.RosterSlots;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.RosterSlot;
import com.fantasy.bff.generated.yahoo.model.StatCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Translates a Yahoo league's settings into the projection-domain shape the web applies.
 *
 * Yahoo bundles the matchup format (head-to-head vs season-long) and the scoring basis
 * (points vs categories) into a single scoring_type string: head/headpoint/headone are
 * head-to-head, roto/point are season-long; head/roto/headone are categories, point/
 * headpoint are points. Only the scoring basis affects player valuation, so the matchup
 * dimension is ignored and the result collapses to points vs category. Unrecognised codes
 * fall back to per-stat pointValue presence (points leagues carry one, category/roto don't).
 */
@Component
public class YahooLeagueSettingsMapper {

    private static final Map<Integer, StatKey> STAT_ID_TO_KEY = Map.ofEntries(
            Map.entry(0, StatKey.GP), Map.entry(29, StatKey.GP), Map.entry(30, StatKey.GP),
            Map.entry(1, StatKey.GOALS), Map.entry(2, StatKey.ASSISTS), Map.entry(3, StatKey.POINTS),
            Map.entry(4, StatKey.PLUS_MINUS), Map.entry(5, StatKey.PIM), Map.entry(6, StatKey.PPG),
            Map.entry(7, StatKey.PPA), Map.entry(8, StatKey.PPP), Map.entry(9, StatKey.SHG),
            Map.entry(10, StatKey.SHA), Map.entry(11, StatKey.SHP), Map.entry(12, StatKey.GWG),
            Map.entry(14, StatKey.SOG), Map.entry(15, StatKey.SH_PCT), Map.entry(16, StatKey.FW),
            Map.entry(17, StatKey.FL), Map.entry(18, StatKey.GS), Map.entry(19, StatKey.W),
            Map.entry(20, StatKey.L), Map.entry(22, StatKey.GA), Map.entry(23, StatKey.GAA),
            Map.entry(24, StatKey.SA), Map.entry(25, StatKey.SV), Map.entry(26, StatKey.SV_PCT),
            Map.entry(27, StatKey.SHO), Map.entry(31, StatKey.HITS), Map.entry(32, StatKey.BLOCKS),
            Map.entry(34, StatKey.TOI_PER_GAME)
    );

    /** Yahoo roster position code -> projection roster slot. W/F are flex slots we approximate as util. */
    private static final Map<String, Slot> POSITION_TO_SLOT = Map.of(
            "C", Slot.C, "LW", Slot.LW, "RW", Slot.RW, "D", Slot.D, "G", Slot.G,
            "BN", Slot.BN, "UTIL", Slot.UTIL, "W", Slot.UTIL, "F", Slot.UTIL
    );

    /** Non-active Yahoo slots that don't belong in a draft roster. */
    private static final Set<String> IGNORED_POSITION_CODES = Set.of("IR", "IR+", "IR-LT", "NA");

    private enum Slot {
        C, LW, RW, D, UTIL, BN, G
    }

    public LeagueProjectionSettingsResponse toProjectionSettings(
            LeagueSettingsResponse settings, Integer numTeams) {
        ScoringBasis scoringType = scoringBasis(settings);

        List<String> activeScoringColumns = new ArrayList<>();
        List<String> activeUtilityColumns = new ArrayList<>();
        List<String> unsupportedStats = new ArrayList<>();
        Map<String, Double> scoredWeights = new LinkedHashMap<>();

        for (StatCategory category : settings.getStatCategories()) {
            StatKey statKey = STAT_ID_TO_KEY.get(category.getStatId());
            if (statKey == null) {
                String label = category.getDisplayName() != null ? category.getDisplayName() : category.getName();
                unsupportedStats.add(label);
                continue;
            }
            String key = statKey.key();
            if (statKey.isUtility()) {
                if (!activeUtilityColumns.contains(key)) {
                    activeUtilityColumns.add(key);
                }
                continue;
            }
            if (!activeScoringColumns.contains(key)) {
                activeScoringColumns.add(key);
            }
            if (scoringType == ScoringBasis.POINTS && category.getPointValue() != null) {
                scoredWeights.put(key, category.getPointValue());
            }
        }

        if (!activeUtilityColumns.contains(StatKey.GP.key())) {
            activeUtilityColumns.add(0, StatKey.GP.key());
        }

        RosterMapping roster = mapRoster(settings.getRosterPositions());

        Map<String, Double> statWeights = scoringType == ScoringBasis.POINTS ? buildWeights(scoredWeights) : null;

        return new LeagueProjectionSettingsResponse(
                scoringType,
                activeScoringColumns,
                activeUtilityColumns,
                statWeights,
                roster.rosterSlots(),
                clampLeagueSize(numTeams).orElse(null),
                unsupportedStats,
                roster.unsupported()
        );
    }

    private ScoringBasis scoringBasis(LeagueSettingsResponse settings) {
        String code = settings.getScoringType() == null ? "" : settings.getScoringType().trim().toLowerCase();
        if (code.equals("point") || code.equals("headpoint")) {
            return ScoringBasis.POINTS;
        }
        if (code.equals("head") || code.equals("roto") || code.equals("headone")) {
            return ScoringBasis.CATEGORY;
        }
        boolean hasWeights = settings.getStatCategories().stream()
                .anyMatch(category -> category.getPointValue() != null);
        return hasWeights ? ScoringBasis.POINTS : ScoringBasis.CATEGORY;
    }

    private record RosterMapping(RosterSlots rosterSlots, List<String> unsupported) {
    }

    private RosterMapping mapRoster(List<RosterSlot> positions) {
        Map<Slot, Integer> counts = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            counts.put(slot, 0);
        }
        List<String> unsupported = new ArrayList<>();
        for (RosterSlot position : positions) {
            String raw = position.getPosition() == null ? "" : position.getPosition();
            String code = raw.toUpperCase();
            int count = position.getCount() == null ? 0 : position.getCount();
            if (IGNORED_POSITION_CODES.contains(code)) {
                unsupported.add(raw);
                continue;
            }
            Slot slot = POSITION_TO_SLOT.get(code);
            if (slot == null) {
                counts.merge(Slot.UTIL, count, Integer::sum);
                unsupported.add(raw);
                continue;
            }
            counts.merge(slot, count, Integer::sum);
            if (code.equals("W") || code.equals("F")) {
                unsupported.add(raw);
            }
        }
        RosterSlots slots = new RosterSlots()
                .c(counts.get(Slot.C)).lw(counts.get(Slot.LW)).rw(counts.get(Slot.RW))
                .d(counts.get(Slot.D)).util(counts.get(Slot.UTIL)).bn(counts.get(Slot.BN)).g(counts.get(Slot.G));
        return new RosterMapping(slots, unsupported);
    }

    private Map<String, Double> buildWeights(Map<String, Double> scored) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (StatKey statKey : StatKey.values()) {
            if (!statKey.isUtility()) {
                weights.put(statKey.key(), scored.getOrDefault(statKey.key(), 0.0));
            }
        }
        return weights;
    }

    private Optional<Integer> clampLeagueSize(Integer numTeams) {
        return Optional.ofNullable(numTeams).map(teams -> Math.min(30, Math.max(2, teams)));
    }
}
