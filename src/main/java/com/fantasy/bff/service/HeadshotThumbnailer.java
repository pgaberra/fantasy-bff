package com.fantasy.bff.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Turns a platform's headshot into the small square PNG the player table draws, framed on the
 * player's head.
 *
 * <p>This lives in the BFF rather than in a pool service because both pools need it and neither
 * can see the other. Yahoo and ESPN each hand back a wide frame of the upper body with the head
 * centred and narrow near the top, and each needs the same square cut out of it; put the rule in
 * one of them and the other keeps its own answer. {@link PlayerPoolSource#getHeadshot} is the one
 * place both meet, so the rule is applied there and there is exactly one of it.
 */
public final class HeadshotThumbnailer {

    /**
     * How wide the square avatar is. The table draws it at 36 CSS pixels, and a browser on a
     * retina screen asks for two device pixels per one of those — 64 was under that and read as
     * soft. 96 clears it, and is the same 2.7x over its own avatar that Yahoo serves for theirs.
     */
    public static final int SIZE = 96;

    /**
     * Decoding a source at full size is the expensive part. The reader subsamples instead, down
     * to the smallest step that still leaves comfortably more detail than the thumbnail needs, so
     * the scale below has something to average over.
     */
    private static final int MIN_DECODED_SIZE = SIZE * 4;

    /** Fraction of the frame's height that holds head and neck and no shoulder. */
    private static final double HEAD_BAND = 0.6;

    /**
     * How wide the square is relative to the head in it — a portrait's worth of air around it.
     *
     * <p>1.55 is what Yahoo's own fantasy avatar works out to on the same pictures, and it is
     * about as tight as the set allows: below roughly 1.45 the taller hair starts losing its top.
     * It also clamps less often than a wider frame does — a square wider than the source's short
     * side gets cut back to it, which quietly hands the widest heads a different framing from
     * everyone else's.
     */
    private static final double FRAME_TO_HEAD = 1.55;

    /** How much of the square sits above the hair. */
    private static final double AIR_ABOVE_HAIR = 0.04;

    private static final int OPAQUE_ENOUGH = 128;

    private HeadshotThumbnailer() {
    }

    public static byte[] toThumbnail(byte[] source) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) {
                throw new IOException("Unreadable image data");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return encodePng(scaleToSquare(crop(decodeSubsampled(reader))));
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage decodeSubsampled(ImageReader reader) throws IOException {
        int shortestSide = Math.min(reader.getWidth(0), reader.getHeight(0));
        int step = Math.max(1, shortestSide / MIN_DECODED_SIZE);
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(step, step, 0, 0);
        return reader.read(0, param);
    }

    /**
     * Frames the crop on the player's head rather than on the middle of the picture.
     *
     * <p>Both platforms serve a wide frame of the upper body: the head sits centred and narrow
     * near the top — around a third of the width — and the shoulders spread across the whole frame
     * below it. A centre crop keeps the full height, which leaves the face filling barely half of
     * the square avatar and reads as small and stretched.
     *
     * <p>The cutout's transparency says where the player is, so the head can be measured rather
     * than assumed: everything in the top {@value #HEAD_BAND} of the frame is head and neck (the
     * shoulders start well below that), so the opaque bounds of that band give the head's width
     * and the top of the hair. Measuring also evens out the difference between a crew cut and a
     * helmet of hair, and it is what lets one rule serve two platforms whose framings differ.
     *
     * <p>Running this over a picture that is already framed on the head is harmless: the head
     * then fills so much of the frame that the square it asks for is larger than the picture, and
     * a square larger than the picture clamps to the whole of it. That is what makes it safe to
     * apply to every source, including a pool service that still crops its own thumbnails.
     *
     * <p>A source with no transparency — or one whose head comes out implausibly small, which
     * means the measurement found something other than a player — falls back to the centre crop.
     */
    private static BufferedImage crop(BufferedImage image) {
        Rectangle head = headFrame(image);
        Rectangle frame = head != null ? head : centreSquare(image);
        return image.getSubimage(frame.x, frame.y, frame.width, frame.height);
    }

    private static Rectangle headFrame(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int band = (int) (height * HEAD_BAND);
        int left = width;
        int right = -1;
        int top = -1;
        for (int y = 0; y < band; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) >>> 24) < OPAQUE_ENOUGH) {
                    continue;
                }
                if (top < 0) {
                    top = y;
                }
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
        }
        if (right < left) {
            return null;
        }
        int shortestSide = Math.min(width, height);
        int side = Math.min((int) ((right - left + 1) * FRAME_TO_HEAD), shortestSide);
        if (side < shortestSide / 4) {
            return null;
        }
        return new Rectangle(
                clamp((left + right) / 2 - side / 2, width - side),
                clamp(top - (int) (side * AIR_ABOVE_HAIR), height - side),
                side,
                side);
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    private static Rectangle centreSquare(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        return new Rectangle(
                (image.getWidth() - side) / 2,
                (image.getHeight() - side) / 2,
                side,
                side);
    }

    private static BufferedImage scaleToSquare(BufferedImage image) {
        BufferedImage current = image;
        // Halving repeatedly before the final step: a single bilinear jump from a few hundred
        // pixels to 64 samples too sparsely and leaves the edges ragged.
        while (current.getWidth() > SIZE * 2) {
            current = drawScaled(current, Math.max(SIZE, current.getWidth() / 2));
        }
        return current.getWidth() == SIZE ? current : drawScaled(current, SIZE);
    }

    private static BufferedImage drawScaled(BufferedImage image, int side) {
        BufferedImage scaled = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, side, side, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("No PNG writer available");
        }
        return out.toByteArray();
    }
}
