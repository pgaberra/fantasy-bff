package com.fantasy.bff.dto.response;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One row of a published board — the same shape going out to a visitor and coming in when the
 * owner publishes, because a share is exactly the rows the client ranked.
 *
 * <p>Ours rather than db-service's generated model, even though the two agree field for field
 * today. This is the shape {@code fantasy-web} generates its client from, and a type the BFF does
 * not own is a contract the BFF cannot promise: a rename downstream, or an artefact of how the
 * models are generated, reshapes our API without anyone deciding to. That is not hypothetical —
 * a phantom {@code tings} property reached the web this way.
 *
 * <p>Every caller-supplied string is capped, because this arrives in a request body and the caps
 * are what stop an oversized payload being stored and served back on a public page. They match
 * what db-service enforces on the same fields; both ends check, neither trusts the other.
 *
 */
public record SharedPlayer(

        /** The platform's player id. */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int playerId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 100) String name,

        @Size(max = 10) String teamAbbrev,

        @Size(max = 300) String headshot,

        @Size(max = 6) List<@Size(max = 4) String> positions,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Type type,

        /** Where the row sits on the published board, counting from 1. */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(1) int rank,

        /** The fantasy value the client ranked by. */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double value,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid PlayerStats stats
) {

    /** Which stat line the row carries. Named on the wire as db-service names it. */
    public enum Type {
        SKATER("skater"),
        GOALIE("goalie");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    /** A row on its way out to a visitor. */
    public static SharedPlayer from(com.fantasy.bff.generated.db.model.SharedPlayer row) {
        return new SharedPlayer(
                row.getPlayerId(),
                row.getName(),
                row.getTeamAbbrev(),
                row.getHeadshot(),
                row.getPositions() == null ? null : List.copyOf(row.getPositions()),
                row.getType() == com.fantasy.bff.generated.db.model.SharedPlayer.TypeEnum.GOALIE
                        ? Type.GOALIE
                        : Type.SKATER,
                row.getRank(),
                row.getValue(),
                PlayerStats.from(row.getStats()));
    }

    /**
     * The same row with no picture, for an environment that shows none. A snapshot keeps the
     * address it was published with, so the switch has to be applied on the way out.
     */
    public SharedPlayer withoutHeadshot() {
        return new SharedPlayer(playerId, name, teamAbbrev, null, positions, type, rank, value, stats);
    }

    /** The same row on its way down to db-service, when the owner publishes. */
    public com.fantasy.bff.generated.db.model.SharedPlayer toDownstream() {
        return new com.fantasy.bff.generated.db.model.SharedPlayer()
                .playerId(playerId)
                .name(name)
                .teamAbbrev(teamAbbrev)
                .headshot(headshot)
                .positions(positions)
                .type(com.fantasy.bff.generated.db.model.SharedPlayer.TypeEnum.fromValue(type.getValue()))
                .rank(rank)
                .value(value)
                .stats(stats.toDownstream());
    }
}
