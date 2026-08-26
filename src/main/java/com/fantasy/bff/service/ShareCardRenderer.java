package com.fantasy.bff.service;

import com.fantasy.bff.generated.db.model.ProjectionSettings;
import com.fantasy.bff.generated.db.model.SharedPlayer;
import com.fantasy.bff.generated.db.model.SharedProjectionResponse;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the picture a shared link unfurls with — the projection's name, who made it, and the top
 * of their board.
 *
 * <p>Drawn server-side rather than screenshotted or uploaded from the browser: the snapshot the
 * card describes already lives here, so the card can never disagree with the page behind the
 * link, and there is no blob to store or keep in step.
 *
 * <p>Java2D needs real fonts on disk. The runtime image installs them (see the Dockerfile) —
 * without that, a JRE container renders every glyph as a box.
 */
@Service
public class ShareCardRenderer {

    // The size every major crawler expects; anything else gets letterboxed or cropped.
    public static final int WIDTH = 1200;
    public static final int HEIGHT = 630;

    private static final int MARGIN = 72;
    private static final int PLAYER_ROWS = 5;
    private static final int TITLE_LINES = 2;

    private static final Color BACKGROUND = new Color(0x0F3460);
    private static final Color PANEL = new Color(0x0C2A4E);
    private static final Color ACCENT = new Color(0xE6B84A);
    private static final Color TEXT = new Color(0xFFFFFF);
    private static final Color TEXT_MUTED = new Color(0xA8BBD6);
    private static final Color VALUE = new Color(0x7FB0FF);

    private static final Font WORDMARK = new Font(Font.SANS_SERIF, Font.BOLD, 28);
    private static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 62);
    private static final Font SUBTITLE = new Font(Font.SANS_SERIF, Font.PLAIN, 28);
    private static final Font ROW = new Font(Font.SANS_SERIF, Font.PLAIN, 32);
    private static final Font ROW_RANK = new Font(Font.SANS_SERIF, Font.BOLD, 32);
    private static final Font FOOTER = new Font(Font.SANS_SERIF, Font.PLAIN, 22);

    public byte[] render(SharedProjectionResponse shared) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paint(graphics, shared);
        } finally {
            graphics.dispose();
        }
        return toPng(canvas);
    }

    private void paint(Graphics2D graphics, SharedProjectionResponse shared) {
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setColor(PANEL);
        graphics.fillRect(WIDTH / 2, 0, WIDTH / 2, HEIGHT);
        graphics.setColor(ACCENT);
        graphics.fillRect(0, 0, WIDTH, 8);

        int textWidth = WIDTH / 2 - MARGIN - 24;

        graphics.setFont(WORDMARK);
        graphics.setColor(ACCENT);
        graphics.drawString("SLAPSTAT · " + season(shared), MARGIN, MARGIN + 20);

        // Wrapped rather than shrunk to fit: a league name is the loudest thing on the card, and
        // there is far more room down the left half than across it.
        List<String> titleLines = wrap(graphics, shared.getName(), TITLE, textWidth, TITLE_LINES);
        graphics.setFont(TITLE);
        graphics.setColor(TEXT);
        int titleBaseline = titleLines.size() > 1 ? 250 : 288;
        for (String line : titleLines) {
            graphics.drawString(line, MARGIN, titleBaseline);
            titleBaseline += 74;
        }

        graphics.setFont(SUBTITLE);
        graphics.setColor(TEXT_MUTED);
        graphics.drawString(fit(graphics, subtitle(shared), SUBTITLE, textWidth), MARGIN,
                titleBaseline + 6);

        paintPlayers(graphics, shared);

        graphics.setFont(FOOTER);
        graphics.setColor(TEXT_MUTED);
        graphics.drawString("slapstat.com", MARGIN, HEIGHT - MARGIN + 12);
        String credit = "Data © MoneyPuck.com and the NHL";
        int creditWidth = graphics.getFontMetrics().stringWidth(credit);
        graphics.drawString(credit, WIDTH - MARGIN - creditWidth, HEIGHT - MARGIN + 12);
    }

    private void paintPlayers(Graphics2D graphics, SharedProjectionResponse shared) {
        List<SharedPlayer> players = shared.getData().getPlayers();
        int left = WIDTH / 2 + 56;
        int right = WIDTH - MARGIN;
        int baseline = 150;

        graphics.setFont(FOOTER);
        graphics.setColor(TEXT_MUTED);
        graphics.drawString(valueLabel(shared).toUpperCase(Locale.ROOT), left, baseline - 34);

        for (int index = 0; index < Math.min(PLAYER_ROWS, players.size()); index++) {
            SharedPlayer player = players.get(index);
            int y = baseline + index * 74;

            graphics.setFont(ROW_RANK);
            graphics.setColor(ACCENT);
            graphics.drawString(String.valueOf(player.getRank()), left, y);

            String formattedValue = String.format(Locale.ROOT, "%.1f", player.getValue());
            graphics.setFont(ROW);
            graphics.setColor(VALUE);
            int valueWidth = graphics.getFontMetrics().stringWidth(formattedValue);
            graphics.drawString(formattedValue, right - valueWidth, y);

            int nameLeft = left + 52;
            graphics.setColor(TEXT);
            graphics.drawString(fit(graphics, player.getName(), ROW, right - valueWidth - nameLeft - 24),
                    nameLeft, y);
        }
    }

    private String subtitle(SharedProjectionResponse shared) {
        String author = shared.getAuthorUsername();
        String scoring = isPointsLeague(shared) ? "Points league" : "Category league";
        Integer leagueSize = shared.getData().getProjectionSettings().getLeagueSize();
        return leagueSize == null
                ? "by " + author + " · " + scoring
                : "by " + author + " · " + scoring + " · " + leagueSize + " teams";
    }

    private String valueLabel(SharedProjectionResponse shared) {
        return isPointsLeague(shared) ? "Fan Pts" : "Z-Score";
    }

    private boolean isPointsLeague(SharedProjectionResponse shared) {
        return ProjectionSettings.ScoringTypeEnum.POINTS == shared.getData().getProjectionSettings().getScoringType();
    }

    /**
     * Breaks text on word boundaries into at most {@code maxLines}; whatever is left over on the
     * final line is trimmed to an ellipsis. A single word longer than the column is trimmed too,
     * so nothing ever runs off the card.
     */
    private List<String> wrap(Graphics2D graphics, String text, Font font, int maxWidth, int maxLines) {
        graphics.setFont(font);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text).trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (graphics.getFontMetrics().stringWidth(candidate) <= maxWidth) {
                line = new StringBuilder(candidate);
                continue;
            }
            if (lines.size() == maxLines - 1) {
                return append(lines, fit(graphics, candidate, font, maxWidth));
            }
            if (line.isEmpty()) {
                lines.add(fit(graphics, word, font, maxWidth));
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        return append(lines, line.toString());
    }

    private List<String> append(List<String> lines, String last) {
        if (!last.isBlank() || lines.isEmpty()) {
            lines.add(last);
        }
        return lines;
    }

    /** Formats the stored season code as the seasons everyone says out loud: 20262027 → 2026-27. */
    private String season(SharedProjectionResponse shared) {
        String code = String.valueOf(shared.getSeason());
        return code.length() == 8 ? code.substring(0, 4) + "-" + code.substring(6) : code;
    }

    /** Trims to an ellipsis rather than letting a long name run off the card. */
    private String fit(Graphics2D graphics, String text, Font font, int maxWidth) {
        String value = text == null ? "" : text;
        graphics.setFont(font);
        if (graphics.getFontMetrics().stringWidth(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "…";
        int ellipsisWidth = graphics.getFontMetrics().stringWidth(ellipsis);
        StringBuilder trimmed = new StringBuilder();
        for (char character : value.toCharArray()) {
            if (graphics.getFontMetrics().stringWidth(trimmed.toString() + character) + ellipsisWidth
                    > maxWidth) {
                break;
            }
            trimmed.append(character);
        }
        return trimmed.append(ellipsis).toString();
    }

    private byte[] toPng(BufferedImage canvas) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(canvas, "png", bytes);
        } catch (IOException e) {
            // Writing to memory cannot fail for I/O reasons; if it does, something is badly wrong
            // and the caller should see it rather than get a blank card.
            throw new UncheckedIOException("Could not encode the share card", e);
        }
        return bytes.toByteArray();
    }
}
