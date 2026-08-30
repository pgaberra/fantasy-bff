package com.fantasy.bff.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeadshotThumbnailerTest {

    @Test
    void producesASquarePngAtTheThumbnailSize() throws IOException {
        BufferedImage thumbnail = thumbnailOf(cutoutShapedSource(WIDTH, HEIGHT));

        assertThat(thumbnail.getWidth()).isEqualTo(HeadshotThumbnailer.SIZE);
        assertThat(thumbnail.getHeight()).isEqualTo(HeadshotThumbnailer.SIZE);
    }

    /**
     * The point of the whole class. A centre crop of a wide source keeps the full height, so the
     * head — narrow, and centred near the top — comes out filling less than a third of the square.
     * Framing on the head gives it appreciably more of the picture.
     */
    @Test
    void framesTheHeadRatherThanTheMiddleOfThePicture() throws IOException {
        BufferedImage thumbnail = thumbnailOf(cutoutShapedSource(WIDTH, HEIGHT));

        assertThat(colourAtCentre(thumbnail)).isEqualTo(HEAD);
        assertThat(shareOf(thumbnail, HEAD)).isGreaterThan(headShareOfACentreCrop());
    }

    /**
     * Both pools come through here, and one of them still frames its own thumbnails before we see
     * them. Framing an already-framed picture has to be a no-op rather than a second bite: the head
     * fills so much of it that the square asked for is bigger than the picture, and that clamps to
     * the whole of it. Without this the two sources could not share the rule.
     */
    @Test
    void leavesAPictureThatIsAlreadyFramedOnTheHeadAlone() throws IOException {
        byte[] once = HeadshotThumbnailer.toThumbnail(cutoutShapedSource(WIDTH, HEIGHT));
        byte[] twice = HeadshotThumbnailer.toThumbnail(once);

        assertThat(shareOf(read(twice), HEAD))
                .isCloseTo(shareOf(read(once), HEAD), org.assertj.core.data.Offset.offset(0.05));
    }

    /**
     * ESPN and Yahoo frame their sources differently — different aspect ratios, different amounts
     * of shoulder. The measurement is what lets one rule serve both, so it has to hold on a source
     * shaped unlike the one it was written against.
     */
    @Test
    void framesTheHeadOnASourceOfADifferentShape() throws IOException {
        BufferedImage thumbnail = thumbnailOf(cutoutShapedSource(264, 192));

        assertThat(colourAtCentre(thumbnail)).isEqualTo(HEAD);
        assertThat(shareOf(thumbnail, HEAD)).isGreaterThan(headShareOfACentreCrop());
    }

    /**
     * The measurement reads the cutout's transparency. A source that has none must not be cropped
     * on whatever it happened to find; it falls back to the middle of the picture.
     */
    @Test
    void fallsBackToTheCentreWhenThereIsNoCutoutToMeasure() throws IOException {
        BufferedImage thumbnail = thumbnailOf(opaqueSource());

        assertThat(colourAtCentre(thumbnail)).isEqualTo(HEAD);
        assertThat(containsAny(thumbnail, MARGIN)).isFalse();
    }

    @Test
    void keepsTheCutoutTransparent() throws IOException {
        BufferedImage thumbnail = thumbnailOf(cutoutShapedSource(WIDTH, HEIGHT));

        assertThat(thumbnail.getColorModel().hasAlpha()).isTrue();
        assertThat(new Color(thumbnail.getRGB(0, 0), true).getAlpha()).isZero();
    }

    @Test
    void rejectsSomethingThatIsNotAnImage() {
        assertThatThrownBy(() -> HeadshotThumbnailer.toThumbnail("not a png".getBytes()))
                .isInstanceOf(IOException.class);
    }

    private static final int WIDTH = 600;
    private static final int HEIGHT = 436;
    private static final Color HEAD = Color.GREEN;
    private static final Color SHOULDERS = Color.BLUE;
    private static final Color MARGIN = Color.RED;

    /** Where the head sits, as fractions of the frame — the same proportions both platforms use. */
    private static final double HEAD_LEFT = 0.36;
    private static final double HEAD_RIGHT = 0.64;
    private static final double HEAD_TOP = 0.05;
    private static final double HEAD_BOTTOM = 0.60;

    private static BufferedImage thumbnailOf(byte[] source) throws IOException {
        return read(HeadshotThumbnailer.toThumbnail(source));
    }

    private static BufferedImage read(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static Color colourAtCentre(BufferedImage image) {
        return new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
    }

    private static double shareOf(BufferedImage image, Color colour) {
        int matched = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (isNear(new Color(image.getRGB(x, y), true), colour)) {
                    matched++;
                }
            }
        }
        return (double) matched / (image.getWidth() * image.getHeight());
    }

    private static boolean containsAny(BufferedImage image, Color colour) {
        return shareOf(image, colour) > 0;
    }

    /** Scaling blends edges, so an exact match would count only the interior of each block. */
    private static boolean isNear(Color actual, Color expected) {
        return actual.getAlpha() > 0
                && Math.abs(actual.getRed() - expected.getRed()) < 40
                && Math.abs(actual.getGreen() - expected.getGreen()) < 40
                && Math.abs(actual.getBlue() - expected.getBlue()) < 40;
    }

    /**
     * The head's share of the square a centre crop would take: that crop is as tall as the source,
     * and the head is neither as wide nor as tall as that, so it comes out under a fifth.
     */
    private static double headShareOfACentreCrop() {
        return (HEAD_RIGHT - HEAD_LEFT) * WIDTH / HEIGHT * (HEAD_BOTTOM - HEAD_TOP);
    }

    /**
     * A stand-in with the proportions of a real cutout: transparent everywhere except a narrow head
     * near the top and shoulders spreading across the bottom.
     */
    private static byte[] cutoutShapedSource(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(HEAD);
        graphics.fillRect((int) (width * HEAD_LEFT), (int) (height * HEAD_TOP),
                (int) (width * (HEAD_RIGHT - HEAD_LEFT)), (int) (height * (HEAD_BOTTOM - HEAD_TOP)));
        graphics.setColor(SHOULDERS);
        graphics.fillRect((int) (width * 0.05), (int) (height * 0.78),
                (int) (width * 0.9), (int) (height * 0.22));
        graphics.dispose();
        return png(image);
    }

    /**
     * The same proportions with nothing transparent to measure, and colour only in the side margins
     * a centre crop drops — so a centre crop is visible in the result.
     */
    private static byte[] opaqueSource() throws IOException {
        int side = Math.min(WIDTH, HEIGHT);
        int left = (WIDTH - side) / 2;

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(MARGIN);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setColor(HEAD);
        graphics.fillRect(left, 0, side, HEIGHT);
        graphics.dispose();
        return png(image);
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
