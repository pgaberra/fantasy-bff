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

        OffsetDateTime finishedAt,

        @Schema(description = "The league this draft is ranked by, set up with the draft. Absent on "
                + "a draft saved before drafts held their own league, which is ranked by the "
                + "projection's settings instead.")
        @Valid DraftSettings settings,

        @Schema(description = "Whether the board follows its linked league's live draft: the sync "
                + "switch as the user left it, so a reload picks the league's draft back up. Absent "
                + "on a board that never followed, which reads as not following.")
        Boolean following
) {

    public static DraftState from(com.fantasy.bff.generated.db.model.DraftState draft) {
        if (draft == null) {
            return null;
        }
        return new DraftState(
                map(draft.getTeams(), DraftTeam::from),
                draft.getOrder(),
                map(draft.getPicks(), DraftPick::from),
                draft.getFinishedAt(),
                DraftSettings.from(draft.getProjectionSettings()),
                draft.getFollowing());
    }

    public com.fantasy.bff.generated.db.model.DraftState toDownstream() {
        return new com.fantasy.bff.generated.db.model.DraftState()
                .teams(map(teams, DraftTeam::toDownstream))
                .order(order)
                .picks(map(picks, DraftPick::toDownstream))
                .finishedAt(finishedAt)
                .projectionSettings(settings == null ? null : settings.toDownstream())
                .following(following);
    }

    private static <S, T> List<T> map(List<S> source, java.util.function.Function<S, T> mapper) {
        return source == null ? null : source.stream().map(mapper).toList();
    }
}
