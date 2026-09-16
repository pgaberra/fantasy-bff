package com.fantasy.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * How a projection is scored and ranked — the league's settings, as the user set them or as a
 * league sync filled them in.
 *
 * <p>The stat keys are the client's, not ours: which stats a league counts follows the platform's
 * settings, so {@code statWeights}, {@code scaleSettings} and {@code decimalSettings} are open
 * maps rather than named fields.
 */
public record ProjectionSettings(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ScoringType scoringType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, Double> statWeights,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> activeScoringColumns,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> activeUtilityColumns,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid Map<String, ScaleConfig> scaleSettings,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, Integer> decimalSettings,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean useDefaultDecimals,

        @Min(2) @Max(30) Integer leagueSize,

        @Valid RosterSlots rosterSlots,

        @Min(0) @Max(82) Integer minGoalieGames,

        @Valid YahooSync yahooSync,

        @Valid EspnSync espnSync,

        @Size(max = 50) String lastEspnLeagueId,

        PlayerBasis playerBasis,

        OffsetDateTime playerPoolSyncedAt,

        @Schema(description = "Players a reconciliation with the player pool added whom the owner "
                + "has not acknowledged yet. Kept until they are, so the app can go on saying so "
                + "across reloads and devices; send it back empty or absent to acknowledge them. "
                + "The cap matches the player list.")
        @Size(max = 2000) List<Integer> unacknowledgedNewPlayerIds
) {

    /** Whether the league scores by points per stat, or by winning categories. */
    public enum ScoringType {
        @JsonProperty("points")
        POINTS,
        @JsonProperty("category")
        CATEGORY
    }

    /** What the projection's player rows started as. */
    public enum PlayerBasis {
        @JsonProperty("last_season")
        LAST_SEASON,
        @JsonProperty("blank")
        BLANK,
        @JsonProperty("model")
        MODEL
    }

    public static ProjectionSettings from(com.fantasy.bff.generated.db.model.ProjectionSettings settings) {
        if (settings == null) {
            return null;
        }
        return new ProjectionSettings(
                settings.getScoringType()
                        == com.fantasy.bff.generated.db.model.ProjectionSettings.ScoringTypeEnum.CATEGORY
                        ? ScoringType.CATEGORY
                        : ScoringType.POINTS,
                settings.getStatWeights(),
                settings.getActiveScoringColumns(),
                settings.getActiveUtilityColumns(),
                scalesFrom(settings.getScaleSettings()),
                settings.getDecimalSettings(),
                Boolean.TRUE.equals(settings.getUseDefaultDecimals()),
                settings.getLeagueSize(),
                RosterSlots.from(settings.getRosterSlots()),
                settings.getMinGoalieGames(),
                YahooSync.from(settings.getYahooSync()),
                EspnSync.from(settings.getEspnSync()),
                settings.getLastEspnLeagueId(),
                basisFrom(settings.getPlayerBasis()),
                settings.getPlayerPoolSyncedAt(),
                settings.getUnacknowledgedNewPlayerIds());
    }

    public com.fantasy.bff.generated.db.model.ProjectionSettings toDownstream() {
        com.fantasy.bff.generated.db.model.ProjectionSettings settings =
                new com.fantasy.bff.generated.db.model.ProjectionSettings()
                        .scoringType(scoringType == ScoringType.CATEGORY
                                ? com.fantasy.bff.generated.db.model.ProjectionSettings.ScoringTypeEnum.CATEGORY
                                : com.fantasy.bff.generated.db.model.ProjectionSettings.ScoringTypeEnum.POINTS)
                        .statWeights(statWeights)
                        .activeScoringColumns(activeScoringColumns)
                        .activeUtilityColumns(activeUtilityColumns)
                        .decimalSettings(decimalSettings)
                        .useDefaultDecimals(useDefaultDecimals)
                        .leagueSize(leagueSize)
                        .rosterSlots(rosterSlots == null ? null : rosterSlots.toDownstream())
                        .minGoalieGames(minGoalieGames)
                        .yahooSync(yahooSync == null ? null : yahooSync.toDownstream())
                        .espnSync(espnSync == null ? null : espnSync.toDownstream())
                        .lastEspnLeagueId(lastEspnLeagueId)
                        .playerPoolSyncedAt(playerPoolSyncedAt)
                        .unacknowledgedNewPlayerIds(unacknowledgedNewPlayerIds);
        settings.setScaleSettings(scalesToDownstream(scaleSettings));
        settings.setPlayerBasis(basisToDownstream(playerBasis));
        return settings;
    }

    private static Map<String, ScaleConfig> scalesFrom(
            Map<String, com.fantasy.bff.generated.db.model.ScaleConfig> scales) {
        if (scales == null) {
            return null;
        }
        return scales.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> ScaleConfig.from(entry.getValue())));
    }

    private static Map<String, com.fantasy.bff.generated.db.model.ScaleConfig> scalesToDownstream(
            Map<String, ScaleConfig> scales) {
        if (scales == null) {
            return null;
        }
        return scales.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue() == null ? null : entry.getValue().toDownstream()));
    }

    private static PlayerBasis basisFrom(
            com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum basis) {
        if (basis == null) {
            return null;
        }
        return switch (basis) {
            case LAST_SEASON -> PlayerBasis.LAST_SEASON;
            case BLANK -> PlayerBasis.BLANK;
            case MODEL -> PlayerBasis.MODEL;
        };
    }

    private static com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum basisToDownstream(
            PlayerBasis basis) {
        if (basis == null) {
            return null;
        }
        return switch (basis) {
            case LAST_SEASON -> com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum.LAST_SEASON;
            case BLANK -> com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum.BLANK;
            case MODEL -> com.fantasy.bff.generated.db.model.ProjectionSettings.PlayerBasisEnum.MODEL;
        };
    }
}
