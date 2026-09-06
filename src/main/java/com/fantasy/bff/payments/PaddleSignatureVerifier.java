package com.fantasy.bff.payments;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Verifies the {@code Paddle-Signature} header Paddle puts on every webhook. The header reads
 * {@code ts=<unix seconds>;h1=<hex hmac>}, and the signed string is the timestamp and the raw
 * body joined by a colon — so the body must be the exact bytes Paddle sent. Re-serializing the
 * JSON would change whitespace and key order and break the signature, which is why the whole
 * path from the controller down takes {@code byte[]}.
 *
 * <p>The timestamp is checked as well as the hash: a signature stays valid forever, so without
 * a freshness bound a captured request could be replayed back at us indefinitely.
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "paddle")
public class PaddleSignatureVerifier {

    public static final String SIGNATURE_HEADER = "Paddle-Signature";

    private final String secret;
    private final long toleranceSeconds;
    private final Clock clock;

    /**
     * {@code @Autowired} is not decoration. There are two constructors and neither takes zero
     * arguments, so without it Spring looks for a default constructor, fails to find one, and
     * the context will not start at all. The unit tests call the other constructor directly and
     * never saw it; a deployment did.
     */
    @Autowired
    public PaddleSignatureVerifier(PaymentsProperties properties) {
        this(properties, Clock.systemUTC());
    }

    PaddleSignatureVerifier(PaymentsProperties properties, Clock clock) {
        this.secret = properties.paddle().webhookSecret();
        this.toleranceSeconds = properties.paddle().signatureToleranceSeconds();
        this.clock = clock;
    }

    /** Throws {@link IllegalArgumentException} unless the header signs exactly these bytes, recently. */
    public void verify(byte[] rawBody, String signatureHeader) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("PADDLE_WEBHOOK_SECRET is not configured");
        }
        if (!StringUtils.hasText(signatureHeader)) {
            throw new IllegalArgumentException("Missing Paddle-Signature header");
        }

        String timestamp = null;
        String provided = null;
        for (String part : signatureHeader.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("ts".equals(pair[0])) {
                timestamp = pair[1];
            } else if ("h1".equals(pair[0])) {
                provided = pair[1];
            }
        }
        if (timestamp == null || provided == null) {
            throw new IllegalArgumentException("Malformed Paddle-Signature header");
        }

        long signedAt;
        try {
            signedAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed Paddle-Signature timestamp", e);
        }
        long ageSeconds = Math.abs(clock.instant().getEpochSecond() - signedAt);
        if (ageSeconds > toleranceSeconds) {
            throw new IllegalArgumentException("Paddle webhook timestamp outside the accepted window");
        }

        byte[] signedPayload = concat((timestamp + ":").getBytes(StandardCharsets.UTF_8), rawBody);
        if (!constantTimeEquals(provided, HexFormat.of().formatHex(hmac(signedPayload)))) {
            throw new IllegalArgumentException("Paddle webhook signature mismatch");
        }
    }

    /** The instant a verified webhook was signed, for callers that want it as the event time. */
    public Instant signedAt(String signatureHeader) {
        for (String part : signatureHeader.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && "ts".equals(pair[0])) {
                return Instant.ofEpochSecond(Long.parseLong(pair[1]));
            }
        }
        throw new IllegalArgumentException("Malformed Paddle-Signature header");
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute Paddle webhook HMAC", e);
        }
    }

    private static byte[] concat(byte[] prefix, byte[] body) {
        byte[] combined = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, combined, 0, prefix.length);
        System.arraycopy(body, 0, combined, prefix.length, body.length);
        return combined;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
