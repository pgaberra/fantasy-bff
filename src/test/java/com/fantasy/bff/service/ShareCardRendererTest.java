package com.fantasy.bff.service;

import com.fantasy.bff.generated.db.model.PlayerStats;
import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.SharedPlayer;
import com.fantasy.bff.generated.db.model.SharedProjectionData;
import com.fantasy.bff.generated.db.model.SharedProjectionResponse;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ShareCardRendererTest {

    private final ShareCardRenderer renderer = new ShareCardRenderer();

    private static SharedPlayer player(int rank, String name) {
        return new SharedPlayer()
                .playerId(rank)
                .name(name)
                .teamAbbrev("EDM")
                .type(SharedPlayer.TypeEnum.SKATER)
                .rank(rank)
                .value(412.5 - rank)
                .stats(new PlayerStats().utility(Map.of("gp", 82.0)).scoring(Map.of("goals", 64.0)));
    }

    private static SharedProjectionResponse shared(String name, String username, List<SharedPlayer> players) {
        ProjectionSettings settings = new ProjectionSettings()
                .scoringType(ProjectionSettings.ScoringTypeEnum.POINTS)
                .statWeights(Map.of("goals", 4.5))
                .activeScoringColumns(List.of("goals"))
                .activeUtilityColumns(List.of("gp"))
                .scaleSettings(Map.of())
                .decimalSettings(Map.of("goals", 0))
                .useDefaultDecimals(true)
                .leagueSize(12);
        return new SharedProjectionResponse()
                .token("abc123")
                .name(name)
                .authorUsername(username)
                .season(SharedProjectionResponse.SeasonEnum._20262027)
                .data(new SharedProjectionData().settings(settings).players(players));
    }

    private static List<SharedPlayer> fivePlayers() {
        List<SharedPlayer> players = new ArrayList<>();
        for (int rank = 1; rank <= 5; rank++) {
            players.add(player(rank, "Player " + rank));
        }
        return players;
    }

    @Test
    void rendersAPngAtTheSizeCrawlersExpect() throws Exception {
        byte[] card = renderer.render(shared("My league", "Alex", fivePlayers()));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(card));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(ShareCardRenderer.WIDTH);
        assertThat(image.getHeight()).isEqualTo(ShareCardRenderer.HEIGHT);
    }

    @Test
    void drawsSomethingRatherThanAFlatBackground() throws Exception {
        byte[] card = renderer.render(shared("My league", "Alex", fivePlayers()));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(card));
        long distinctColours = java.util.stream.IntStream.range(0, image.getHeight())
                .boxed()
                .flatMap(y -> java.util.stream.IntStream.range(0, image.getWidth())
                        .mapToObj(x -> image.getRGB(x, y)))
                .distinct()
                .count();
        // Background, panel, accent bar and antialiased text: a card that failed to draw would
        // come back with a handful of colours at most.
        assertThat(distinctColours).isGreaterThan(20L);
    }

    @Test
    void survivesAProjectionWithNoRows() {
        assertThatCode(() -> renderer.render(shared("Empty", "alex", List.of()))).doesNotThrowAnyException();
    }

    @Test
    void survivesNamesFarTooLongForTheCard() {
        String longName = "A projection name that just keeps going and going ".repeat(6);
        List<SharedPlayer> players = List.of(player(1, "A player name that also refuses to end ".repeat(4)));

        assertThatCode(() -> renderer.render(shared(longName, "An alias that is also very long indeed",
                players))).doesNotThrowAnyException();
    }
}
