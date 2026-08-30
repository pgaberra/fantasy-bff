package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A draft in progress, or the record of one that finished. The picks are kept in order, because
 * the order is the draft — replaying them is how a board is rebuilt.
 */
public record DraftState(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 32) @Valid List<DraftTeam> teams,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 32) List<@Size(max = 64) String> order,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid List<DraftPick> picks,

        OffsetDateTime finishedAt
) {

    public static DraftState from(com.fantasy.bff.generated.db.model.DraftState draft) {
        if (draft == null) {
            return null;
        }
        return new DraftState(
                map(draft.getTeams(), DraftTeam::from),
                draft.getOrder(),
                map(draft.getPicks(), DraftPick::from),
                draft.getFinishedAt());
    }

    public com.fantasy.bff.generated.db.model.DraftState toDownstream() {
        return new com.fantasy.bff.generated.db.model.DraftState()
                .teams(map(teams, DraftTeam::toDownstream))
                .order(order)
                .picks(map(picks, DraftPick::toDownstream))
                .finishedAt(finishedAt);
    }

    private static <S, T> List<T> map(List<S> source, java.util.function.Function<S, T> mapper) {
        return source == null ? null : source.stream().map(mapper).toList();
    }
}
