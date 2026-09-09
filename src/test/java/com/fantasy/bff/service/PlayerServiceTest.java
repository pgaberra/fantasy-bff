package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerPoolSource playerPool;

    @Mock
    private PlayerSplitContextProvider playerContext;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        // The model knows no better by default, so every existing case sees the pool untouched.
        lenient().when(playerContext.currentTeams()).thenReturn(Map.of());
        playerService = new PlayerService(playerPool, new HeadshotCache(), playerContext);
    }

    /** What the model would say, keyed by the platform id the pool uses. */
    private void modelSays(Map<Integer, String> teams) {
        when(playerContext.currentTeams()).thenReturn(teams);
    }

    private static SkaterResponse mcDavid() {
        return new SkaterResponse(1, "Connor McDavid", "EDM", "/players/1/headshot", 97,
                Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(82, 1320),
                        new SkaterResponse.ScoringStats(64, 89, 153, 33, 36, 22, 38, 60, 1, 0, 1,
                                23, 38, 61, 8, 0, 348, 18.4, 812, 623, 42, 28, 0, 0, 108240)));
    }

    private static GoalieResponse shesterkin() {
        return new GoalieResponse(101, "Igor Shesterkin", "NYR", "/players/101/headshot", 31,
                new GoalieResponse.Stats(
                        new GoalieResponse.UtilityStats(58),
                        new GoalieResponse.ScoringStats(58, 36, 17, 5, 3, 1720, 1565, 155, 2.67,
                                0.910, 0.621, 209000)));
    }

    @Test
    void getSkaters_servesWhateverTheConfiguredSourceHolds() {
        when(playerPool.getSkaters(nullable(Integer.class))).thenReturn(List.of(mcDavid()));

        List<SkaterResponse> result = playerService.getSkaters();

        assertThat(result).singleElement()
                .satisfies(skater -> assertThat(skater.name()).isEqualTo("Connor McDavid"));
    }

    @Test
    void getGoalies_servesWhateverTheConfiguredSourceHolds() {
        when(playerPool.getGoalies(nullable(Integer.class))).thenReturn(List.of(shesterkin()));

        assertThat(playerService.getGoalies()).singleElement()
                .satisfies(goalie -> assertThat(goalie.name()).isEqualTo("Igor Shesterkin"));
    }

    private static SkaterResponse skater(int id, String name, int points) {
        return new SkaterResponse(id, name, "EDM", "/players/" + id + "/headshot", 97,
                Set.of(SkaterPosition.C),
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(82, 1320),
                        new SkaterResponse.ScoringStats(0, 0, points, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0.0, 0, 0, 0, 0, 0, 0, 0)));
    }

    private static GoalieResponse goalie(int id, String name, int wins, int saves) {
        return new GoalieResponse(id, name, "NYR", "/players/" + id + "/headshot", 31,
                new GoalieResponse.Stats(
                        new GoalieResponse.UtilityStats(58),
                        new GoalieResponse.ScoringStats(0, wins, 0, 0, 0, 0, saves, 0, 0.0,
                                0.0, 0.0, 0)));
    }

    @Test
    void getSkaters_servesTheHighestScoringFirst() {
        when(playerPool.getSkaters(nullable(Integer.class))).thenReturn(List.of(
                skater(1, "Middle", 80), skater(2, "Best", 120), skater(3, "Worst", 40)));

        assertThat(playerService.getSkaters()).extracting(SkaterResponse::name)
                .containsExactly("Best", "Middle", "Worst");
    }

    // The order is what makes the limit meaningful: five rows of a preview want the five the
    // board opens with, not five arbitrary players.
    @Test
    void getSkaters_withALimit_servesThatManyFromTheTop() {
        when(playerPool.getSkaters(nullable(Integer.class))).thenReturn(List.of(
                skater(1, "Middle", 80), skater(2, "Best", 120), skater(3, "Worst", 40)));

        assertThat(playerService.getSkaters(2)).extracting(SkaterResponse::name)
                .containsExactly("Best", "Middle");
    }

    @Test
    void getSkaters_withALimitLargerThanThePool_servesThePool() {
        when(playerPool.getSkaters(nullable(Integer.class))).thenReturn(List.of(skater(1, "Only", 80)));

        assertThat(playerService.getSkaters(50)).hasSize(1);
    }

    @Test
    void getGoalies_servesTheMostWinsFirstAndHonoursTheLimit() {
        when(playerPool.getGoalies(nullable(Integer.class))).thenReturn(List.of(
                goalie(1, "Fewest", 12, 900), goalie(2, "Most", 39, 1300),
                goalie(3, "Middle", 30, 1400)));

        assertThat(playerService.getGoalies(2)).extracting(GoalieResponse::name)
                .containsExactly("Most", "Middle");
    }

    // Two goalies on the same wins are separated by saves, and never by chance: the same request
    // has to answer the same way twice, or a cached page and a fresh one disagree.
    @Test
    void getGoalies_breaksTiesOnSaves() {
        when(playerPool.getGoalies(nullable(Integer.class))).thenReturn(List.of(
                goalie(1, "Fewer saves", 30, 1200), goalie(2, "More saves", 30, 1400)));

        assertThat(playerService.getGoalies(null)).extracting(GoalieResponse::name)
                .containsExactly("More saves", "Fewer saves");
    }

    // Asking the source for the slice is what keeps the rest of the pool off the wire between
    // the services; cutting it here as well is what keeps the answer right when a source can't.
    @Test
    void getSkaters_asksTheSourceForOnlyTheSliceItWillKeep() {
        when(playerPool.getSkaters(2)).thenReturn(List.of(
                skater(1, "Middle", 80), skater(2, "Best", 120), skater(3, "Worst", 40)));

        assertThat(playerService.getSkaters(2)).extracting(SkaterResponse::name)
                .containsExactly("Best", "Middle");
        verify(playerPool).getSkaters(2);
    }

    @Test
    void getGoalies_asksTheSourceForOnlyTheSliceItWillKeep() {
        when(playerPool.getGoalies(1)).thenReturn(List.of(goalie(1, "Most", 39, 1300)));

        assertThat(playerService.getGoalies(1)).hasSize(1);
        verify(playerPool).getGoalies(1);
    }

    /**
     * Both pools serve a wide frame of the upper body and both need the same square cut out of it,
     * so the framing is done here rather than in either of them.
     */
    @Test
    void getHeadshot_framesWhateverTheSourceServes() throws Exception {
        when(playerPool.getHeadshot(1)).thenReturn(Optional.of(wideCutout()));

        byte[] served = playerService.getHeadshot(1).orElseThrow();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(image.getWidth()).isEqualTo(HeadshotThumbnailer.SIZE);
        assertThat(image.getHeight()).isEqualTo(HeadshotThumbnailer.SIZE);
    }

    /**
     * The point of holding one: a page of avatars asks for fifty at once, and the expensive part
     * is fetching each source from the platform, not framing it. The second reader must not send
     * the pool asking again.
     */
    @Test
    void getHeadshot_drawsItOnceAndServesTheSameBytesAfter() throws Exception {
        when(playerPool.getHeadshot(1)).thenReturn(Optional.of(wideCutout()));

        byte[] first = playerService.getHeadshot(1).orElseThrow();
        byte[] second = playerService.getHeadshot(1).orElseThrow();

        assertThat(second).isSameAs(first);
        verify(playerPool, times(1)).getHeadshot(1);
    }

    /**
     * A pool with no picture for a player is one whose sync is expected to fill it in later, so a
     * remembered miss would outlive the reason for it.
     */
    @Test
    void getHeadshot_doesNotRememberThatThereWasNoPicture() {
        when(playerPool.getHeadshot(1)).thenReturn(Optional.empty());

        assertThat(playerService.getHeadshot(1)).isEmpty();
        assertThat(playerService.getHeadshot(1)).isEmpty();

        verify(playerPool, times(2)).getHeadshot(1);
    }

    /**
     * A fetch that fails is answered like a player with no picture, not like a fault: an avatar
     * timing out against a CDN used to be a 502 and a fault alarm. It is not remembered either,
     * so the next reader of that player tries the source again.
     */
    @Test
    void getHeadshot_isEmptyWhenTheSourceCannotBeReached() {
        when(playerPool.getHeadshot(1)).thenThrow(new RuntimeException("read timed out"));

        assertThat(playerService.getHeadshot(1)).isEmpty();
        assertThat(playerService.getHeadshot(1)).isEmpty();

        verify(playerPool, times(2)).getHeadshot(1);
    }

    /**
     * One picture that will not decode is not worth failing the request over — the player keeps
     * what the source handed over, which is a real picture, just framed the way that platform
     * framed it.
     */
    @Test
    void getHeadshot_servesWhatItCannotFrameRatherThanFailing() {
        when(playerPool.getHeadshot(1)).thenReturn(Optional.of(new byte[] {1, 2, 3}));

        assertThat(playerService.getHeadshot(1)).contains(new byte[] {1, 2, 3});
    }

    @Test
    void getHeadshot_isEmptyWhenTheSourceHasNoPicture() {
        when(playerPool.getHeadshot(1)).thenReturn(Optional.empty());

        assertThat(playerService.getHeadshot(1)).isEmpty();
    }

    /** A wide frame with a narrow head near the top, as both platforms serve. */
    private static byte[] wideCutout() throws Exception {
        BufferedImage image = new BufferedImage(600, 436, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(216, 22, 168, 240);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(30, 340, 540, 96);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void getSkaters_whenTheSourceThrows_throwsIllegalStateException() {
        when(playerPool.getSkaters(nullable(Integer.class))).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> playerService.getSkaters())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to retrieve skaters from player service");
    }

    @Test
    void getGoalies_whenTheSourceThrows_throwsIllegalStateException() {
        when(playerPool.getGoalies(nullable(Integer.class))).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> playerService.getGoalies())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to retrieve goalies from player service");
    }

    // The pool's team is whatever its platform held at its last sync. The projection service
    // follows the NHL daily, so where the two disagree the model is the one that is current.

    @Test
    void getSkaters_movesASkaterToTheTeamTheModelHasHimOn() {
        when(playerPool.getSkaters(nullable(Integer.class))).thenReturn(List.of(mcDavid()));
        modelSays(Map.of(1, "FLA"));

        assertThat(playerService.getSkaters()).singleElement()
                .satisfies(skater -> assertThat(skater.teamAbbrev()).isEqualTo("FLA"));
    }

    @Test
    void getGoalies_movesAGoalieToTheTeamTheModelHasHimOn() {
        when(playerPool.getGoalies(nullable(Integer.class))).thenReturn(List.of(shesterkin()));
        modelSays(Map.of(101, "FLA"));

        assertThat(playerService.getGoalies()).singleElement()
                .satisfies(goalie -> assertThat(goalie.teamAbbrev()).isEqualTo("FLA"));
    }

    @Test
    void getSkaters_leavesEverythingElseAboutThePlayerAlone() {
        when(playerPool.getSkaters(nullable(Integer.class))).thenReturn(List.of(mcDavid()));
        modelSays(Map.of(1, "FLA"));

        assertThat(playerService.getSkaters()).singleElement().satisfies(skater -> {
            assertThat(skater.id()).isEqualTo(1);
            assertThat(skater.name()).isEqualTo("Connor McDavid");
            assertThat(skater.headshot()).isEqualTo("/players/1/headshot");
            assertThat(skater.sweaterNumber()).isEqualTo(97);
            assertThat(skater.positions()).containsExactly(SkaterPosition.C);
            assertThat(skater.stats().scoring().points()).isEqualTo(153);
        });
    }

    @Test
    void getGoalies_leavesEverythingElseAboutThePlayerAlone() {
        when(playerPool.getGoalies(nullable(Integer.class))).thenReturn(List.of(shesterkin()));
        modelSays(Map.of(101, "FLA"));

        assertThat(playerService.getGoalies()).singleElement().satisfies(goalie -> {
            assertThat(goalie.id()).isEqualTo(101);
            assertThat(goalie.name()).isEqualTo("Igor Shesterkin");
            assertThat(goalie.headshot()).isEqualTo("/players/101/headshot");
            assertThat(goalie.sweaterNumber()).isEqualTo(31);
            assertThat(goalie.stats().scoring().w()).isEqualTo(36);
        });
    }

    @Test
    void getSkaters_keepsThePoolsTeamForAPlayerTheModelHasNoAnswerFor() {
        when(playerPool.getSkaters(nullable(Integer.class))).thenReturn(List.of(mcDavid()));
        // An older club is a better answer than none, so a missing entry changes nothing.
        modelSays(Map.of(999, "FLA"));

        assertThat(playerService.getSkaters()).singleElement()
                .satisfies(skater -> assertThat(skater.teamAbbrev()).isEqualTo("EDM"));
    }

    @Test
    void getSkaters_servesThePoolAsItIsWhenTheModelCannotBeReached() {
        when(playerPool.getSkaters(nullable(Integer.class))).thenReturn(List.of(mcDavid()));
        // The provider degrades to an empty map rather than raising: losing the correction must
        // not cost the pool, which is the app's spine.
        modelSays(Map.of());

        assertThat(playerService.getSkaters()).singleElement()
                .satisfies(skater -> assertThat(skater.teamAbbrev()).isEqualTo("EDM"));
    }

    @Test
    void getSkaters_asksTheModelOnceForTheWholeList() {
        when(playerPool.getSkaters(nullable(Integer.class)))
                .thenReturn(List.of(skater(1, "A", 100), skater(2, "B", 90), skater(3, "C", 80)));
        modelSays(Map.of(1, "FLA"));

        playerService.getSkaters();

        verify(playerContext, times(1)).currentTeams();
    }
}
