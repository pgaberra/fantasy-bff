package com.fantasy.bff.mapper;

import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.dto.response.ScoringBasis;
import com.fantasy.bff.generated.db.model.RosterSlots;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.RosterSlot;
import com.fantasy.bff.generated.yahoo.model.StatCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    private static final Map<Integer, String> STAT_ID_TO_KEY = Map.ofEntries(
            Map.entry(0, "gp"), Map.entry(29, "gp"), Map.entry(30, "gp"),
            Map.entry(1, "goals"), Map.entry(2, "assists"), Map.entry(3, "points"),
            Map.entry(4, "plusMinus"), Map.entry(5, "pim"), Map.entry(6, "ppg"),
            Map.entry(7, "ppa"), Map.entry(8, "ppp"), Map.entry(9, "shg"),
            Map.entry(10, "sha"), Map.entry(11, "shp"), Map.entry(12, "gwg"),
            Map.entry(14, "sog"), Map.entry(15, "shPct"), Map.entry(16, "fw"),
            Map.entry(17, "fl"), Map.entry(18, "gs"), Map.entry(19, "w"),
            Map.entry(20, "l"), Map.entry(22, "ga"), Map.entry(23, "gaa"),
            Map.entry(24, "sa"), Map.entry(25, "sv"), Map.entry(26, "svPct"),
            Map.entry(27, "sho"), Map.entry(31, "hits"), Map.entry(32, "blocks"),
            Map.entry(34, "toiPerGame")
    );

    private static final Map<String, String> POSITION_TO_SLOT = Map.of(
            "C", "c", "LW", "lw", "RW", "rw", "D", "d", "G", "g", "BN", "bn",
            "UTIL", "util", "W", "util", "F", "util"
    );

    private static final Set<String> IGNORED_POSITION_CODES = Set.of("IR", "IR+", "IR-LT", "NA");

    private static final Set<String> UTILITY_KEYS = Set.of("gp", "toiPerGame");

    private static final List<String> SCORING_STAT_KEYS = List.of(
            "goals", "assists", "points", "plusMinus", "pim", "ppg", "ppa", "ppp",
            "shg", "sha", "shp", "gwg", "sog", "shPct", "fw", "fl", "hits", "blocks",
            "gs", "w", "l", "sho", "sa", "sv", "ga", "gaa", "svPct"
    );

    public LeagueProjectionSettingsResponse toProjectionSettings(
            LeagueSettingsResponse settings, Integer numTeams) {
        ScoringBasis scoringType = scoringBasis(settings);

        List<String> activeScoringColumns = new ArrayList<>();
        List<String> activeUtilityColumns = new ArrayList<>();
        List<String> unsupportedStats = new ArrayList<>();
        Map<String, Double> scoredWeights = new LinkedHashMap<>();

        for (StatCategory category : settings.getStatCategories()) {
            String key = STAT_ID_TO_KEY.get(category.getStatId());
            if (key == null) {
                String label = category.getDisplayName() != null ? category.getDisplayName() : category.getName();
                unsupportedStats.add(label);
                continue;
            }
            if (UTILITY_KEYS.contains(key)) {
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

        if (!activeUtilityColumns.contains("gp")) {
            activeUtilityColumns.add(0, "gp");
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
        int c = 0, lw = 0, rw = 0, d = 0, util = 0, bn = 0, g = 0;
        List<String> unsupported = new ArrayList<>();
        for (RosterSlot slot : positions) {
            String raw = slot.getPosition() == null ? "" : slot.getPosition();
            String code = raw.toUpperCase();
            int count = slot.getCount() == null ? 0 : slot.getCount();
            if (IGNORED_POSITION_CODES.contains(code)) {
                unsupported.add(raw);
                continue;
            }
            String bucket = POSITION_TO_SLOT.get(code);
            if (bucket == null) {
                util += count;
                unsupported.add(raw);
                continue;
            }
            switch (bucket) {
                case "c" -> c += count;
                case "lw" -> lw += count;
                case "rw" -> rw += count;
                case "d" -> d += count;
                case "util" -> util += count;
                case "bn" -> bn += count;
                case "g" -> g += count;
                default -> { }
            }
            if (code.equals("W") || code.equals("F")) {
                unsupported.add(raw);
            }
        }
        RosterSlots slots = new RosterSlots().c(c).lw(lw).rw(rw).d(d).util(util).bn(bn).g(g);
        return new RosterMapping(slots, unsupported);
    }

    private Map<String, Double> buildWeights(Map<String, Double> scored) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String key : SCORING_STAT_KEYS) {
            weights.put(key, scored.getOrDefault(key, 0.0));
        }
        return weights;
    }

    private Optional<Integer> clampLeagueSize(Integer numTeams) {
        return Optional.ofNullable(numTeams).map(teams -> Math.min(30, Math.max(2, teams)));
    }
}
