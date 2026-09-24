package com.fantasy.bff.dto.response;

import com.fantasy.bff.dto.request.UpdateProjectionData;
import com.fantasy.bff.generated.db.model.DraftPick;
import com.fantasy.bff.generated.db.model.DraftSettings;
import com.fantasy.bff.generated.db.model.DraftState;
import com.fantasy.bff.generated.db.model.DraftTeam;
import com.fantasy.bff.generated.db.model.ManualRanking;
import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.PlayerTypeRanking;
import com.fantasy.bff.generated.db.model.PositionOverride;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.RosterSlots;
import com.fantasy.bff.generated.db.model.ScaleConfig;
import com.fantasy.bff.generated.db.model.YahooSync;
import com.fantasy.bff.generated.db.model.EspnSync;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A saved projection now crosses a mapping in both directions, and its object graph is deep:
 * settings that carry roster slots and two league syncs, a row per player, and a draft with its
 * teams, order and picks. A mapper that drops one field of that is invisible everywhere else —
 * a save would simply come back without the draft, or without the roster slots, and look fine
 * until someone reloaded the page.
 *
 * <p>So the assertion is equality on db-service's own model, which covers every property it has.
 * It fails the day a field is added downstream and not carried across, which is the point.
 */
class ProjectionGraphMappingTest {

    private static final OffsetDateTime SYNCED_AT =
            OffsetDateTime.of(2026, 8, 1, 9, 30, 0, 0, ZoneOffset.UTC);

    private static ProjectionSettings settings() {
        ProjectionSettings settings = new ProjectionSettings()
                .scoringType(ProjectionSettings.ScoringTypeEnum.CATEGORY)
                .statWeights(Map.of("goals", 4.5, "assists", 3.0))
                .activeScoringColumns(List.of("goals", "assists"))
                .activeUtilityColumns(List.of("hits"))
                .decimalSettings(Map.of("goals", 1))
                .useDefaultDecimals(true)
                .leagueSize(12)
                .rosterSlots(new RosterSlots().c(2).lw(2).rw(2).d(4).util(1).bn(4).g(2))
                .minGoalieGames(30)
                .yahooSync(new YahooSync()
                        .leagueName("The League").leagueKey("465.l.78677").syncedAt(SYNCED_AT))
                .espnSync(new EspnSync()
                        .leagueName("Other League").leagueId("12345").syncedAt(SYNCED_AT))
                .lastEspnLeagueId("12345")
                .playerPoolSyncedAt(SYNCED_AT)
                .unacknowledgedNewPlayerIds(List.of(8478402))
                .manualRanking(new ManualRanking()
                        .skater(new PlayerTypeRanking()
                                .mode(PlayerTypeRanking.ModeEnum.PROJECTED))
                        .goalie(new PlayerTypeRanking()
                                .mode(PlayerTypeRanking.ModeEnum.MANUAL)
                                .order(List.of(8471734, 8476945))));
        settings.setScaleSettings(Map.of("scoring", new ScaleConfig()
                .scale(true).scalableStats(List.of("goals"))));
        settings.setPlayerBasis(ProjectionSettings.PlayerBasisEnum.LAST_SEASON);
        return settings;
    }

    private static PlayerProjection row(int playerId, PlayerProjection.TypeEnum type) {
        return new PlayerProjection()
                .playerId(playerId)
                .type(type)
                .stats(new PlayerStats()
                        .scoring(Map.of("goals", 42.0))
                        .utility(Map.of("hits", 60.0)));
    }

    private static DraftState draft() {
        return new DraftState()
                .teams(List.of(new DraftTeam().id("t1").name("Mine").mine(true),
                        new DraftTeam().id("t2").name("Theirs").mine(false)))
                .order(List.of("t1", "t2"))
                .picks(List.of(new DraftPick().playerId(8478402).teamId("t1")))
                .finishedAt(SYNCED_AT)
                .projectionSettings(new DraftSettings()
                        .scoringType(DraftSettings.ScoringTypeEnum.CATEGORY)
                        .statWeights(Map.of("goals", 6.0))
                        .activeScoringColumns(List.of("goals", "hits"))
                        .activeUtilityColumns(List.of("gp"))
                        .leagueSize(10)
                        .rosterSlots(new RosterSlots().c(2).lw(2).rw(2).d(4).util(1).bn(4).g(2))
                        .minGoalieGames(84)
                        .espnSync(new EspnSync()
                                .leagueName("Other League").leagueId("12345").syncedAt(SYNCED_AT))
                        .lastEspnLeagueId("12345"))
                .following(true);
    }

    @Test
    void carriesEverySettingBothWays() {
        ProjectionSettings original = settings();

        assertThat(com.fantasy.bff.dto.response.ProjectionSettings.from(original).toDownstream())
                .isEqualTo(original);
    }

    @Test
    void carriesASettingsBlockWithNothingOptionalSet() {
        ProjectionSettings sparse = new ProjectionSettings()
                .scoringType(ProjectionSettings.ScoringTypeEnum.POINTS)
                .statWeights(Map.of())
                .activeScoringColumns(List.of())
                .activeUtilityColumns(List.of())
                .decimalSettings(Map.of())
                .useDefaultDecimals(false);
        sparse.setScaleSettings(Map.of());

        var mapped = com.fantasy.bff.dto.response.ProjectionSettings.from(sparse);

        assertThat(mapped.leagueSize()).isNull();
        assertThat(mapped.rosterSlots()).isNull();
        assertThat(mapped.yahooSync()).isNull();
        assertThat(mapped.espnSync()).isNull();
        assertThat(mapped.playerBasis()).isNull();
        assertThat(mapped.toDownstream()).isEqualTo(sparse);
    }

    @Test
    void carriesADraftBothWays() {
        DraftState original = draft();

        assertThat(com.fantasy.bff.dto.response.DraftState.from(original).toDownstream())
                .isEqualTo(original);
    }

    /**
     * A draft that never followed its league says nothing about it, and has to come back saying
     * nothing: mapped to false, the next save would write a choice the user never made.
     */
    @Test
    void leavesFollowingUnsetOnADraftThatNeverFollowed() {
        DraftState never = new DraftState()
                .teams(List.of(new DraftTeam().id("t1").name("Mine").mine(true)))
                .order(List.of("t1"))
                .picks(List.of());

        var mapped = com.fantasy.bff.dto.response.DraftState.from(never);

        assertThat(mapped.following()).isNull();
        assertThat(mapped.toDownstream()).isEqualTo(never);
    }

    @Test
    void keepsAGoalieAGoalie() {
        PlayerProjection goalie = row(8479973, PlayerProjection.TypeEnum.GOALIE);

        var mapped = com.fantasy.bff.dto.response.PlayerProjection.from(goalie);

        assertThat(mapped.type())
                .isEqualTo(com.fantasy.bff.dto.response.PlayerProjection.Type.GOALIE);
        assertThat(mapped.toDownstream()).isEqualTo(goalie);
    }

    /**
     * The save path is the one that matters most: what the client sends is what gets stored, and
     * anything this mapping drops is data the user typed and will not get back.
     */
    @Test
    void carriesAWholeSaveDownToDbService() {
        var owned = new UpdateProjectionData(
                com.fantasy.bff.dto.response.ProjectionSettings.from(settings()),
                List.of(com.fantasy.bff.dto.response.PlayerProjection.from(
                                row(8478402, PlayerProjection.TypeEnum.SKATER)),
                        com.fantasy.bff.dto.response.PlayerProjection.from(
                                row(8479973, PlayerProjection.TypeEnum.GOALIE))),
                com.fantasy.bff.dto.response.DraftState.from(draft()),
                List.of(new com.fantasy.bff.dto.response.PositionOverride(8478402,
                        List.of(com.fantasy.bff.dto.response.SkaterPosition.C,
                                com.fantasy.bff.dto.response.SkaterPosition.LW))));

        var downstream = owned.toDownstream();

        assertThat(downstream.getProjectionSettings()).isEqualTo(settings());
        assertThat(downstream.getPlayers()).containsExactly(
                row(8478402, PlayerProjection.TypeEnum.SKATER),
                row(8479973, PlayerProjection.TypeEnum.GOALIE));
        assertThat(downstream.getDraft()).isEqualTo(draft());
        assertThat(downstream.getPositionOverrides()).containsExactly(
                new PositionOverride()
                        .playerId(8478402)
                        .positions(List.of(PositionOverride.PositionsEnum.C,
                                PositionOverride.PositionsEnum.LW)));
    }

    /**
     * Resetting every player back to the read model's positions is a save that carries an empty
     * list, and it has to arrive as one: dropped to null it would read as "unchanged" downstream.
     */
    @Test
    void carriesAnEmptyOverrideListAsItself() {
        var owned = new UpdateProjectionData(
                com.fantasy.bff.dto.response.ProjectionSettings.from(settings()), null, null,
                List.of());

        assertThat(owned.toDownstream().getPositionOverrides()).isEmpty();
    }

    /** An autosave that only changed the scoring sends neither the board nor the draft. */
    @Test
    void carriesASaveThatIsOnlySettings() {
        var owned = new UpdateProjectionData(
                com.fantasy.bff.dto.response.ProjectionSettings.from(settings()), null, null, null);

        var downstream = owned.toDownstream();

        assertThat(downstream.getProjectionSettings()).isEqualTo(settings());
        assertThat(downstream.getPlayers()).isNull();
        assertThat(downstream.getDraft()).isNull();
        assertThat(downstream.getPositionOverrides()).isNull();
    }
}
