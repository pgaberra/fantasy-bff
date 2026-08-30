package com.fantasy.bff.dto.response;

import com.fantasy.bff.generated.db.model.PlayerStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The row now crosses a mapping in both directions — out of db-service on the way to a visitor,
 * and back down when the owner publishes. A mapper that quietly drops a field is the whole risk
 * of owning the type, and it would not show up as a failure anywhere else: the board would simply
 * be published without headshots, or without positions, and look fine.
 */
class SharedPlayerTest {

    private static com.fantasy.bff.generated.db.model.SharedPlayer downstreamRow() {
        return new com.fantasy.bff.generated.db.model.SharedPlayer()
                .playerId(8478402)
                .name("Connor McDavid")
                .teamAbbrev("EDM")
                .headshot("/players/8478402/headshot")
                .positions(List.of("C", "LW"))
                .type(com.fantasy.bff.generated.db.model.SharedPlayer.TypeEnum.SKATER)
                .rank(1)
                .value(742.5)
                .stats(new PlayerStats()
                        .scoring(Map.of("goals", 64.0, "assists", 89.0))
                        .utility(Map.of("hits", 41.0)));
    }

    @Test
    void carriesEveryFieldOutOfDbService() {
        SharedPlayer row = SharedPlayer.from(downstreamRow());

        assertThat(row.playerId()).isEqualTo(8478402);
        assertThat(row.name()).isEqualTo("Connor McDavid");
        assertThat(row.teamAbbrev()).isEqualTo("EDM");
        assertThat(row.headshot()).isEqualTo("/players/8478402/headshot");
        assertThat(row.positions()).containsExactly("C", "LW");
        assertThat(row.type()).isEqualTo(SharedPlayer.Type.SKATER);
        assertThat(row.rank()).isEqualTo(1);
        assertThat(row.value()).isEqualTo(742.5);
        assertThat(row.stats().getScoring()).containsEntry("goals", 64.0);
        assertThat(row.stats().getUtility()).containsEntry("hits", 41.0);
    }

    /**
     * Equality on the generated model covers every property it has, so this fails the day one is
     * added downstream and not carried across — which is the failure worth catching early.
     */
    @Test
    void survivesTheRoundTripBackDown() {
        com.fantasy.bff.generated.db.model.SharedPlayer original = downstreamRow();

        assertThat(SharedPlayer.from(original).toDownstream()).isEqualTo(original);
    }

    @Test
    void keeps_a_goalie_a_goalie() {
        com.fantasy.bff.generated.db.model.SharedPlayer goalie = downstreamRow()
                .type(com.fantasy.bff.generated.db.model.SharedPlayer.TypeEnum.GOALIE);

        SharedPlayer row = SharedPlayer.from(goalie);

        assertThat(row.type()).isEqualTo(SharedPlayer.Type.GOALIE);
        assertThat(row.type().getValue()).isEqualTo("goalie");
        assertThat(row.toDownstream()).isEqualTo(goalie);
    }

    /**
     * The optional fields are absent far more often than not — a rookie call-up has none.
     *
     * <p>{@code positions} comes back empty rather than null because db-service's model
     * initialises the list, which is what the response carried before this type was ours. Kept
     * that way deliberately: the JSON a visitor receives must not change.
     */
    @Test
    void survivesTheRoundTripWithNothingOptionalSet() {
        com.fantasy.bff.generated.db.model.SharedPlayer sparse = new com.fantasy.bff.generated.db.model.SharedPlayer()
                .playerId(1)
                .name("A Prospect")
                .type(com.fantasy.bff.generated.db.model.SharedPlayer.TypeEnum.SKATER)
                .rank(300)
                .value(0.0)
                .stats(new PlayerStats().scoring(Map.of()).utility(Map.of()));

        SharedPlayer row = SharedPlayer.from(sparse);

        assertThat(row.teamAbbrev()).isNull();
        assertThat(row.headshot()).isNull();
        assertThat(row.positions()).isEmpty();
        assertThat(row.toDownstream()).isEqualTo(sparse);
    }

    /** A goalie has no positions at all, and null is not the same as "none listed". */
    @Test
    void keepsAnAbsentPositionsListAbsent() {
        com.fantasy.bff.generated.db.model.SharedPlayer noPositions = downstreamRow().positions(null);

        SharedPlayer row = SharedPlayer.from(noPositions);

        assertThat(row.positions()).isNull();
        assertThat(row.toDownstream()).isEqualTo(noPositions);
    }
}
