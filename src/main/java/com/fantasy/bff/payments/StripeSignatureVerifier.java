package com.fantasy.bff.payments;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Verifies the {@code Stripe-Signature} header Stripe puts on every webhook. The header reads
 * {@code t=<unix seconds>,v1=<hex hmac>[,v1=…][,v0=…]}, and the signed string is the timestamp and
 * the raw body joined by a dot, so the body must be the exact bytes Stripe sent (see
 * {@link PaddleSignatureVerifier} for why the path down from the controller stays {@code byte[]}).
 *
 * <p>Any one {@code v1} matching is enough: Stripe sends one per active endpoint secret, and while
 * a secret is being rolled both the old and the new one sign. {@code v0} is a test-mode scheme and
 * is ignored. The timestamp is bounded too, since a signature on its own stays valid forever.
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "stripe")
public class StripeSignatureVerifier {

    public static final String SIGNATURE_HEADER = "Stripe-Signature";

    private final String secret;
    private final long toleranceSeconds;
    private final Clock clock;

    /** {@code @Autowired} for the reason {@link PaddleSignatureVerifier}'s constructor gives. */
    @Autowired
    public StripeSignatureVerifier(PaymentsProperties properties) {
        this(properties, Clock.systemUTC());
    }

    StripeSignatureVerifier(PaymentsProperties properties, Clock clock) {
        this.secret = properties.stripe().webhookSecret();
        this.toleranceSeconds = properties.stripe().signatureToleranceSeconds();
        this.clock = clock;
    }

    /** Throws {@link IllegalArgumentException} unless the header signs exactly these bytes, recently. */
    public void verify(byte[] rawBody, String signatureHeader) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET is not configured");
        }
        if (!StringUtils.hasText(signatureHeader)) {
            throw new IllegalArgumentException("Missing Stripe-Signature header");
        }

        String timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                timestamp = pair[1];
            } else if ("v1".equals(pair[0])) {
                signatures.add(pair[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new IllegalArgumentException("Malformed Stripe-Signature header");
        }

        long signedAt;
        try {
            signedAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed Stripe-Signature timestamp", e);
        }
        long ageSeconds = Math.abs(clock.instant().getEpochSecond() - signedAt);
        if (ageSeconds > toleranceSeconds) {
            throw new IllegalArgumentException("Stripe webhook timestamp outside the accepted window");
        }

        byte[] expected = HexFormat.of().formatHex(hmac(concat((timestamp + ".").getBytes(StandardCharsets.UTF_8),
                rawBody))).getBytes(StandardCharsets.UTF_8);
        boolean matched = false;
        for (String signature : signatures) {
            // Every candidate is compared, and in constant time, so the answer takes as long
            // whichever of them (if any) matches.
            matched |= MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
        }
        if (!matched) {
            throw new IllegalArgumentException("Stripe webhook signature mismatch");
        }
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute Stripe webhook HMAC", e);
        }
    }

    private static byte[] concat(byte[] prefix, byte[] body) {
        byte[] combined = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, combined, 0, prefix.length);
        System.arraycopy(body, 0, combined, prefix.length, body.length);
        return combined;
    }
}
