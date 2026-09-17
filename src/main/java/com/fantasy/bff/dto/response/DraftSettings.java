package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * The league a draft is ranked by, held by the draft itself so that setting a draft up never
 * changes the projection it is played against. The league half of {@link ProjectionSettings}:
 * how it scores, on which stats, its size and roster, and where it was imported from.
 */
public record DraftSettings(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ProjectionSettings.ScoringType scoringType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(max = 100) Map<@NotBlank @Size(max = 32) String, @NotNull Double> statWeights,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 32) String> activeScoringColumns,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 32) String> activeUtilityColumns,

        @Schema(description = "Number of teams. Category leagues only — absent for points leagues.")
        @Min(2) @Max(30) Integer leagueSize,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Roster slots per team; the draft's rosters are built from them.")
        @NotNull @Valid RosterSlots rosterSlots,

        @Schema(description = "Minimum projected games for a goalie to qualify in category "
                + "ranking, up to a full 84-game season. Category leagues only.")
        @Min(0) @Max(84) Integer minGoalieGames,

        @Valid YahooSync yahooSync,

        @Valid EspnSync espnSync,

        @Size(max = 50) String lastEspnLeagueId
) {

    public static DraftSettings from(com.fantasy.bff.generated.db.model.DraftSettings settings) {
        if (settings == null) {
            return null;
        }
        return new DraftSettings(
                settings.getScoringType()
                        == com.fantasy.bff.generated.db.model.DraftSettings.ScoringTypeEnum.CATEGORY
                        ? ProjectionSettings.ScoringType.CATEGORY
                        : ProjectionSettings.ScoringType.POINTS,
                settings.getStatWeights(),
                settings.getActiveScoringColumns(),
                settings.getActiveUtilityColumns(),
                settings.getLeagueSize(),
                RosterSlots.from(settings.getRosterSlots()),
                settings.getMinGoalieGames(),
                YahooSync.from(settings.getYahooSync()),
                EspnSync.from(settings.getEspnSync()),
                settings.getLastEspnLeagueId());
    }

    public com.fantasy.bff.generated.db.model.DraftSettings toDownstream() {
        return new com.fantasy.bff.generated.db.model.DraftSettings()
                .scoringType(scoringType == ProjectionSettings.ScoringType.CATEGORY
                        ? com.fantasy.bff.generated.db.model.DraftSettings.ScoringTypeEnum.CATEGORY
                        : com.fantasy.bff.generated.db.model.DraftSettings.ScoringTypeEnum.POINTS)
                .statWeights(statWeights)
                .activeScoringColumns(activeScoringColumns)
                .activeUtilityColumns(activeUtilityColumns)
                .leagueSize(leagueSize)
                // Required, so validated present before anything is sent on.
                .rosterSlots(rosterSlots.toDownstream())
                .minGoalieGames(minGoalieGames)
                .yahooSync(yahooSync == null ? null : yahooSync.toDownstream())
                .espnSync(espnSync == null ? null : espnSync.toDownstream())
                .lastEspnLeagueId(lastEspnLeagueId);
    }
}
