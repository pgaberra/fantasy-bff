package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "The player the model ranked highest of those still undrafted when a pick was made")
public record DraftAnalysisAlternative(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The player, by the pool's own id.")
        int playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "His NHL club as the pool spells it; absent where it lists none") String club,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Eligible positions, C/LW/RW/D/G")
        List<String> positions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "His rank in the model by value over replacement, from 1")
        int aiRank,
        @Schema(description = "His rank among the players at his position, as \"D3\"") String positionRank) {}
