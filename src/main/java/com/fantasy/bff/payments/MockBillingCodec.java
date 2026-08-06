package com.fantasy.bff.payments;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * HMAC-SHA256 signing for the mock payment provider. It both signs the short opaque token
 * carried on the mock checkout/portal stub URLs (so the confirm/cancel handlers can trust the
 * user id) and signs the mock webhook body the way a real provider would. Mirrors the
 * yahoo-service OAuthStateCodec. Only wired when the mock provider is active.
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockBillingCodec {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final String secret;

    public MockBillingCodec(PaymentsProperties properties) {
        this.secret = properties.mock().webhookSecret();
    }

    /** Signs the user id into a {@code payload.signature} token for a stub checkout/portal URL. */
    public String encodeToken(String userId) {
        String payloadB64 = ENC.encodeToString(userId.getBytes(StandardCharsets.UTF_8));
        return payloadB64 + "." + ENC.encodeToString(hmac(payloadB64.getBytes(StandardCharsets.UTF_8)));
    }

    /** Returns the user id if the token is well-formed and correctly signed. */
    public String decodeToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Missing billing token");
        }
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed billing token");
        }
        String expected = ENC.encodeToString(hmac(parts[0].getBytes(StandardCharsets.UTF_8)));
        if (!constantTimeEquals(parts[1], expected)) {
            throw new IllegalArgumentException("Billing token signature mismatch");
        }
        return new String(DEC.decode(parts[0]), StandardCharsets.UTF_8);
    }

    /** Base64url HMAC of a raw webhook body, for the mock {@code X-Mock-Signature} header. */
    public String signBody(byte[] body) {
        return ENC.encodeToString(hmac(body));
    }

    /** Constant-time check that a provided signature matches the body. */
    public boolean bodySignatureValid(byte[] body, String providedSignature) {
        if (!StringUtils.hasText(providedSignature)) {
            return false;
        }
        return constantTimeEquals(providedSignature, signBody(body));
    }

    private byte[] hmac(byte[] data) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("PAYMENTS_MOCK_WEBHOOK_SECRET is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute mock billing HMAC", e);
        }
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
