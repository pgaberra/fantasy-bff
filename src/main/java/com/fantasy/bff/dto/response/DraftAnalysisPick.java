package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One pick of a league's draft, set against the model's ranking")
public record DraftAnalysisPick(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Overall pick number, from 1.")
        int overall,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int round,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The id of the team that made the pick.")
        String teamId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The player, by the pool's own id.")
        int playerId,
        @Schema(description = "The player's name; absent where the pool does not carry him") String name,
        @Schema(description = "His NHL club as the pool spells it; absent where it lists none") String club,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Eligible positions, C/LW/RW/D/G; empty where the pool does not carry him")
        List<String> positions,
        @Schema(description = "His rank in the model under the league's scoring, from 1. Absent without "
                + "premium, and for a player the model has no line for.")
        Integer aiRank,
        @Schema(description = "His value in the model: fantasy points in a points league, the summed "
                + "z-score in a category league. Absent without premium, and for a player the model "
                + "has no line for.")
        Double value,
        @Schema(description = "His value less the value of the player the model ranks at this pick's "
                + "number: what the pick gained or gave away against taking the model's player there. "
                + "Absent without premium, and for a player the model has no line for.")
        Double valueOverSlot,
        @Schema(description = "The pick's grade. Absent without premium, and in an auction draft.")
        DraftPickGrade grade,
        @Schema(description = "The player the model ranked highest of those still undrafted, where he "
                + "ranked above the one taken. Absent without premium.")
        DraftAnalysisAlternative bestAvailable) {

    /** The same pick with nothing of the model's in it, for an account without premium. */
    public DraftAnalysisPick withoutModel() {
        return new DraftAnalysisPick(
                overall, round, teamId, playerId, name, club, positions, null, null, null, null, null);
    }
}
