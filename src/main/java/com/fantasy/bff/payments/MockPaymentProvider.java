package com.fantasy.bff.payments;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * In-repo payment provider used for local/test mode: it drives the full subscription lifecycle
 * without any external service. Checkout/portal URLs point at the BFF's own
 * {@code /api/v1/billing/mock/**} stub pages, whose confirm/cancel actions self-POST a signed
 * webhook to the real {@code /api/v1/billing/webhook} — exercising the same signature-verify +
 * persistence path a real provider would. Active by default; a real provider replaces it via
 * {@code payments.provider}.
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    public static final String SIGNATURE_HEADER = "X-Mock-Signature";

    private final MockBillingCodec codec;
    private final ObjectMapper objectMapper;
    private final String selfBaseUrl;

    public MockPaymentProvider(MockBillingCodec codec, ObjectMapper objectMapper, PaymentsProperties properties) {
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.selfBaseUrl = properties.mock().selfBaseUrl();
    }

    @Override
    public String id() {
        return "mock";
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        return new CheckoutSession(
                selfBaseUrl + "/api/v1/billing/mock/checkout?token=" + codec.encodeToken(request.userId()));
    }

    @Override
    public PortalSession createPortalSession(PortalRequest request) {
        return new PortalSession(
                selfBaseUrl + "/api/v1/billing/mock/portal?token=" + codec.encodeToken(request.userId()));
    }

    @Override
    public WebhookEvent parseAndVerify(byte[] rawBody, HttpHeaders headers) {
        if (!codec.bodySignatureValid(rawBody, headers.getFirst(SIGNATURE_HEADER))) {
            throw new IllegalArgumentException("Invalid mock webhook signature");
        }
        try {
            return objectMapper.readValue(rawBody, WebhookEvent.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed mock webhook body", e);
        }
    }
}
