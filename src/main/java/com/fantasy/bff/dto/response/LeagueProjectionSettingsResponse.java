package com.fantasy.bff.dto.response;

import com.fantasy.bff.generated.db.model.RosterSlots;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public record LeagueProjectionSettingsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScoringBasis scoringType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> activeScoringColumns,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> activeUtilityColumns,
        @Schema(description = "Per-stat point weights for points leagues; null for category leagues.")
        Map<String, Double> statWeights,
        @Schema(description = "The league's own name, so a synced projection can say where its "
                + "settings came from. Set for ESPN, whose settings response carries it; null for "
                + "Yahoo, where the client picked the league by name from the league list.")
        String leagueName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RosterSlots rosterSlots,
        @Schema(description = "League size from the Yahoo league's team count; null when unknown.")
        Integer leagueSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> unsupportedStats,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> unsupportedRosterCodes
) {}
