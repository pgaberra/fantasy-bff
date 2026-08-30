package com.fantasy.bff.dto.response;

import com.fantasy.bff.dto.request.ProjectionKind;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * A saved projection as it appears in a list: enough to name it and say what state it is in,
 * without carrying the board. The rows are the bulk of a projection and the list never shows
 * them, so they are not sent.
 */
public record ProjectionSummaryResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProjectionKind kind,

        @Schema(description = "Which shared starting point a preset draft was started from. "
                + "Absent on any other kind, and on preset drafts saved before this was "
                + "recorded. This, not the name, is what tells two preset drafts apart.")
        ProjectionPreset preset,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Season season,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DraftStatus draftStatus,

        ProjectionResponse.ProjectionOrigin origin
) {

    /** The season a projection is for, as its 8-digit code. */
    public enum Season {
        @JsonProperty("20252026")
        SEASON_2025_2026,
        @JsonProperty("20262027")
        SEASON_2026_2027,
        @JsonProperty("20272028")
        SEASON_2027_2028,
        @JsonProperty("20282029")
        SEASON_2028_2029
    }

    /**
     * Which shared starting point a preset draft came from. A draft records that it came from a
     * preset in its kind; this says which, so nothing has to read it back out of the name.
     */
    public enum ProjectionPreset {
        @JsonProperty("last_season")
        LAST_SEASON,
        @JsonProperty("model")
        MODEL
    }

    /** How far a draft against this projection has got, if one was ever started. */
    public enum DraftStatus {
        @JsonProperty("none")
        NONE,
        @JsonProperty("in_progress")
        IN_PROGRESS,
        @JsonProperty("finished")
        FINISHED
    }

    public static ProjectionSummaryResponse from(
            com.fantasy.bff.generated.db.model.ProjectionSummaryResponse summary) {
        com.fantasy.bff.generated.db.model.ProjectionOrigin origin = summary.getOrigin();
        return new ProjectionSummaryResponse(
                summary.getId(),
                summary.getName(),
                kindOf(summary.getKind()),
                presetOf(summary.getPreset()),
                seasonOf(summary.getSeason()),
                summary.getCreatedAt(),
                summary.getUpdatedAt(),
                draftStatusOf(summary.getDraftStatus()),
                origin == null
                        ? null
                        : new ProjectionResponse.ProjectionOrigin(
                                origin.getShareToken(), origin.getAuthorUsername()));
    }

    private static ProjectionKind kindOf(
            com.fantasy.bff.generated.db.model.ProjectionSummaryResponse.KindEnum kind) {
        if (kind == null) {
            return ProjectionKind.PROJECTION;
        }
        return switch (kind) {
            case PRESET_DRAFT -> ProjectionKind.PRESET_DRAFT;
            case IMPORTED -> ProjectionKind.IMPORTED;
            case PROJECTION -> ProjectionKind.PROJECTION;
        };
    }

    private static ProjectionPreset presetOf(
            com.fantasy.bff.generated.db.model.ProjectionSummaryResponse.PresetEnum preset) {
        if (preset == null) {
            return null;
        }
        return switch (preset) {
            case LAST_SEASON -> ProjectionPreset.LAST_SEASON;
            case MODEL -> ProjectionPreset.MODEL;
        };
    }

    private static Season seasonOf(
            com.fantasy.bff.generated.db.model.ProjectionSummaryResponse.SeasonEnum season) {
        if (season == null) {
            return null;
        }
        return switch (season) {
            case _20252026 -> Season.SEASON_2025_2026;
            case _20262027 -> Season.SEASON_2026_2027;
            case _20272028 -> Season.SEASON_2027_2028;
            case _20282029 -> Season.SEASON_2028_2029;
        };
    }

    private static DraftStatus draftStatusOf(
            com.fantasy.bff.generated.db.model.ProjectionSummaryResponse.DraftStatusEnum status) {
        if (status == null) {
            return DraftStatus.NONE;
        }
        return switch (status) {
            case IN_PROGRESS -> DraftStatus.IN_PROGRESS;
            case FINISHED -> DraftStatus.FINISHED;
            case NONE -> DraftStatus.NONE;
        };
    }
}
