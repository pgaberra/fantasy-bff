package com.fantasy.bff.payments;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentProviderTest {

    private static final String SECRET = "test-mock-secret";
    private static final String SELF = "http://localhost:8080";

    private final MockBillingCodec codec = new MockBillingCodec(
            new PaymentsProperties(true, "mock", new PaymentsProperties.Mock(SECRET, SELF), null, null));
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final MockPaymentProvider provider = new MockPaymentProvider(codec, objectMapper,
            new PaymentsProperties(true, "mock", new PaymentsProperties.Mock(SECRET, SELF), null, null));

    @Test
    void createCheckoutSession_returnsSignedStubUrlCarryingUserId() {
        CheckoutSession session = provider.createCheckoutSession(new CheckoutRequest("user-1", "s", "c", null));

        assertThat(session.url()).startsWith(SELF + "/api/v1/billing/mock/checkout?token=");
        String token = session.url().substring(session.url().indexOf("token=") + "token=".length());
        assertThat(codec.decodeToken(token)).isEqualTo("user-1");
    }

    @Test
    void createPortalSession_returnsSignedStubUrl() {
        PortalSession session = provider.createPortalSession(new PortalRequest("user-1", "cus_1", "r"));

        assertThat(session.url()).startsWith(SELF + "/api/v1/billing/mock/portal?token=");
    }

    @Test
    void parseAndVerify_roundTripsAValidSignedWebhook() throws Exception {
        WebhookEvent event = new WebhookEvent("evt-1", Instant.now(), WebhookEventType.SUBSCRIPTION_CREATED,
                new SubscriptionSnapshot("user-1", "cus_1", "sub_1", "price_1", "active",
                        Instant.now().plus(30, ChronoUnit.DAYS), false));
        byte[] body = objectMapper.writeValueAsBytes(event);
        HttpHeaders headers = new HttpHeaders();
        headers.add(MockPaymentProvider.SIGNATURE_HEADER, codec.signBody(body));

        WebhookEvent parsed = provider.parseAndVerify(body, headers);

        assertThat(parsed.subscription().userId()).isEqualTo("user-1");
        assertThat(parsed.type()).isEqualTo(WebhookEventType.SUBSCRIPTION_CREATED);
        assertThat(parsed.subscription().status()).isEqualTo("active");
    }

    @Test
    void parseAndVerify_rejectsATamperedBody() throws Exception {
        byte[] signedBody = objectMapper.writeValueAsBytes(new WebhookEvent("evt-1", Instant.now(),
                WebhookEventType.SUBSCRIPTION_CREATED,
                new SubscriptionSnapshot("user-1", "cus_1", "sub_1", "price_1", "active", null, false)));
        String signature = codec.signBody(signedBody);
        byte[] tampered = objectMapper.writeValueAsBytes(new WebhookEvent("evt-2", Instant.now(),
                WebhookEventType.SUBSCRIPTION_CANCELED,
                new SubscriptionSnapshot("user-2", "cus_2", "sub_2", "price_1", "canceled", null, false)));
        HttpHeaders headers = new HttpHeaders();
        headers.add(MockPaymentProvider.SIGNATURE_HEADER, signature);

        assertThatThrownBy(() -> provider.parseAndVerify(tampered, headers))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseAndVerify_rejectsAMissingSignature() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(new WebhookEvent("evt-1", Instant.now(),
                WebhookEventType.SUBSCRIPTION_CREATED,
                new SubscriptionSnapshot("user-1", "cus_1", "sub_1", "price_1", "active", null, false)));

        assertThatThrownBy(() -> provider.parseAndVerify(body, new HttpHeaders()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
