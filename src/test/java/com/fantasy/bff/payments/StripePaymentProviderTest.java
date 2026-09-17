package com.fantasy.bff.payments;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StripePaymentProviderTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String USER_ID = "3f1a5b6c-0000-4000-8000-000000000001";
    private static final String PRICE_ID = "price_premium_monthly";
    private static final Instant NOW = Instant.parse("2026-09-17T08:00:00Z");
    private static final String API = "https://api.stripe.test";
    private static final String CHECKOUT_RESPONSE = """
            {"id":"cs_test_1","object":"checkout.session","status":"open",
             "url":"https://checkout.stripe.com/c/pay/cs_test_1"}""";

    private MockRestServiceServer stripeServer;

    private StripePaymentProvider provider(boolean managedPayments) {
        PaymentsProperties properties = new PaymentsProperties(true, "stripe", null, null,
                new PaymentsProperties.Stripe("sk_test_key", SECRET, PRICE_ID, managedPayments, 300));
        RestClient.Builder builder = RestClient.builder().baseUrl(API);
        stripeServer = MockRestServiceServer.bindTo(builder).build();
        return new StripePaymentProvider(builder.build(), new ObjectMapper(),
                new StripeSignatureVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC)), properties);
    }

    private static CheckoutRequest checkoutFor(String customerEmail) {
        return new CheckoutRequest(USER_ID, "https://slapstat.test/premium?checkout=success",
                "https://slapstat.test/premium?checkout=cancel", customerEmail);
    }

    /**
     * The user goes on the subscription's metadata, which is what every later webhook reads back,
     * and the price on the session's own, which is what reuse compares against.
     */
    @Test
    void checkoutSessionCarriesTheUserAndPriceAndReturnsStripesUrl() {
        StripePaymentProvider provider = provider(true);
        stripeServer.expect(requestTo(API + "/v1/checkout/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "mode", "subscription",
                        "line_items[0][price]", PRICE_ID,
                        "line_items[0][quantity]", "1",
                        "success_url", "https://slapstat.test/premium?checkout=success",
                        "cancel_url", "https://slapstat.test/premium?checkout=cancel",
                        "client_reference_id", USER_ID,
                        "subscription_data[metadata][user_id]", USER_ID,
                        "metadata[price_id]", PRICE_ID,
                        "managed_payments[enabled]", "true")))
                .andExpect(content().string(not(containsString("customer_email"))))
                .andRespond(withSuccess(CHECKOUT_RESPONSE, MediaType.APPLICATION_JSON));

        CheckoutSession session = provider.createCheckoutSession(checkoutFor(null));

        assertThat(session.url()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_1");
        assertThat(session.reference()).isEqualTo("cs_test_1");
        stripeServer.verify();
    }

    @Test
    void aVerifiedEmailIsPrefilled() {
        StripePaymentProvider provider = provider(true);
        stripeServer.expect(requestTo(API + "/v1/checkout/sessions"))
                .andExpect(content().formDataContains(Map.of("customer_email", "owner+stripe@example.com")))
                .andRespond(withSuccess(CHECKOUT_RESPONSE, MediaType.APPLICATION_JSON));

        provider.createCheckoutSession(checkoutFor("owner+stripe@example.com"));

        stripeServer.verify();
    }

    /** Switched off, the checkout is an ordinary one with us as the seller, so nothing asks for it. */
    @Test
    void managedPaymentsIsOnlyRequestedWhenSwitchedOn() {
        StripePaymentProvider provider = provider(false);
        stripeServer.expect(requestTo(API + "/v1/checkout/sessions"))
                .andExpect(content().string(not(containsString("managed_payments"))))
                .andRespond(withSuccess(CHECKOUT_RESPONSE, MediaType.APPLICATION_JSON));

        provider.createCheckoutSession(checkoutFor(null));

        stripeServer.verify();
    }

    @Test
    void aCheckoutWithoutAConfiguredPriceIsOurFault() {
        PaymentsProperties properties = new PaymentsProperties(true, "stripe", null, null,
                new PaymentsProperties.Stripe("sk_test_key", SECRET, "", true, 300));
        StripePaymentProvider provider = new StripePaymentProvider(RestClient.create(API), new ObjectMapper(),
                new StripeSignatureVerifier(properties), properties);

        assertThatThrownBy(() -> provider.createCheckoutSession(checkoutFor(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_PRICE_ID");
    }

    /**
     * Stripe answers about our request, not the user's: an account not approved for Managed
     * Payments, a wrong key. It must reach the logs as a fault (502), with Stripe's own words kept
     * in the cause rather than handed to the browser.
     */
    @Test
    void aRejectionFromStripeIsOurFaultNotTheCallers() {
        StripePaymentProvider provider = provider(true);
        stripeServer.expect(requestTo(API + "/v1/checkout/sessions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"type":"invalid_request_error",
                                 "message":"Managed Payments is not enabled for this account."}}"""));

        assertThatThrownBy(() -> provider.createCheckoutSession(checkoutFor(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("create a checkout")
                .hasMessageNotContaining("Managed Payments is not enabled")
                .cause()
                .hasMessageContaining("Managed Payments is not enabled");
    }

    private void expectSession(String status, String sessionPriceId) {
        stripeServer.expect(requestTo(API + "/v1/checkout/sessions/cs_test_1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"cs_test_1","status":"%s","metadata":{"price_id":"%s","user_id":"%s"},
                         "url":"https://checkout.stripe.com/c/pay/cs_test_1"}"""
                        .formatted(status, sessionPriceId, USER_ID), MediaType.APPLICATION_JSON));
    }

    @Test
    void anOpenSessionOnTheCurrentPriceCanStillBePaid() {
        StripePaymentProvider provider = provider(true);
        expectSession("open", PRICE_ID);

        assertThat(provider.isCheckoutOpen("cs_test_1")).isTrue();
        stripeServer.verify();
    }

    @Test
    void aCompletedSessionIsNotReused() {
        StripePaymentProvider provider = provider(true);
        expectSession("complete", PRICE_ID);

        assertThat(provider.isCheckoutOpen("cs_test_1")).isFalse();
    }

    @Test
    void anExpiredSessionIsNotReused() {
        StripePaymentProvider provider = provider(true);
        expectSession("expired", PRICE_ID);

        assertThat(provider.isCheckoutOpen("cs_test_1")).isFalse();
    }

    @Test
    void aSessionForAPriceNoLongerSoldIsNotReused() {
        StripePaymentProvider provider = provider(true);
        expectSession("open", "price_old");

        assertThat(provider.isCheckoutOpen("cs_test_1")).isFalse();
    }

    @Test
    void aSessionThatCannotBeReadIsNotReused() {
        StripePaymentProvider provider = provider(true);
        stripeServer.expect(requestTo(API + "/v1/checkout/sessions/cs_test_1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"invalid_request_error\"}}"));

        assertThat(provider.isCheckoutOpen("cs_test_1")).isFalse();
    }

    @Test
    void portalSessionReturnsStripesUrl() {
        StripePaymentProvider provider = provider(true);
        stripeServer.expect(requestTo(API + "/v1/billing_portal/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "customer", "cus_1",
                        "return_url", "https://slapstat.test/premium?portal=return")))
                .andRespond(withSuccess("{\"id\":\"bps_1\",\"url\":\"https://billing.stripe.com/p/session/x\"}",
                        MediaType.APPLICATION_JSON));

        PortalSession session = provider.createPortalSession(
                new PortalRequest(USER_ID, "cus_1", "https://slapstat.test/premium?portal=return"));

        assertThat(session.url()).isEqualTo("https://billing.stripe.com/p/session/x");
        stripeServer.verify();
    }

    @Test
    void aRejectedPortalSessionIsOurFaultToo() {
        StripePaymentProvider provider = provider(true);
        stripeServer.expect(requestTo(API + "/v1/billing_portal/sessions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"No configuration provided\"}}"));

        assertThatThrownBy(() -> provider.createPortalSession(
                new PortalRequest(USER_ID, "cus_1", "https://slapstat.test/premium")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("open the billing portal");
    }

    @Test
    void aCreatedSubscriptionIsParsedForTheUserInItsMetadata() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.created", """
                "status":"active","cancel_at_period_end":false,"cancel_at":null,
                "items":{"data":[{"price":{"id":"price_premium_monthly"},"current_period_end":1760687940}]}""");

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.type()).isEqualTo(WebhookEventType.SUBSCRIPTION_CREATED);
        assertThat(event.occurredAt()).isEqualTo(Instant.ofEpochSecond(1758095940L));
        SubscriptionSnapshot snapshot = event.subscription();
        assertThat(snapshot.userId()).isEqualTo(USER_ID);
        assertThat(snapshot.providerCustomerId()).isEqualTo("cus_1");
        assertThat(snapshot.providerSubscriptionId()).isEqualTo("sub_1");
        assertThat(snapshot.priceId()).isEqualTo(PRICE_ID);
        assertThat(snapshot.status()).isEqualTo("active");
        assertThat(snapshot.currentPeriodEnd()).isEqualTo(Instant.ofEpochSecond(1760687940L));
        assertThat(snapshot.cancelAtPeriodEnd()).isFalse();
    }

    /** An endpoint created on an API version before basil still sends the period on the subscription. */
    @Test
    void thePeriodEndIsReadFromTheSubscriptionWhenTheItemHasNone() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.updated", """
                "status":"active","current_period_end":1760687940,
                "items":{"data":[{"price":{"id":"price_premium_monthly"}}]}""");

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.subscription().currentPeriodEnd()).isEqualTo(Instant.ofEpochSecond(1760687940L));
    }

    @Test
    void aCancellationAtPeriodEndIsReported() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.updated", """
                "status":"active","cancel_at_period_end":true,
                "items":{"data":[{"price":{"id":"price_premium_monthly"},"current_period_end":1760687940}]}""");

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.type()).isEqualTo(WebhookEventType.SUBSCRIPTION_UPDATED);
        assertThat(event.subscription().cancelAtPeriodEnd()).isTrue();
    }

    /** The billing portal on newer API versions schedules the end as a date with the flag left false. */
    @Test
    void aCancelAtDateIsAPendingCancellationToo() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.updated", """
                "status":"active","cancel_at_period_end":false,"cancel_at":1760687940,
                "items":{"data":[{"price":{"id":"price_premium_monthly"},"current_period_end":1760687940}]}""");

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.subscription().cancelAtPeriodEnd()).isTrue();
    }

    @Test
    void aDeletedSubscriptionIsACancellation() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.deleted", """
                "status":"canceled","items":{"data":[{"price":{"id":"price_premium_monthly"}}]}""");

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.type()).isEqualTo(WebhookEventType.SUBSCRIPTION_CANCELED);
        assertThat(event.subscription().status()).isEqualTo("canceled");
    }

    @Test
    void anUpdateThatLeavesItPastDueIsReportedAsPastDue() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.updated", """
                "status":"past_due","items":{"data":[{"price":{"id":"price_premium_monthly"}}]}""");

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.type()).isEqualTo(WebhookEventType.SUBSCRIPTION_PAST_DUE);
    }

    /** db-service has no incomplete_expired; a first payment that never cleared has simply ended. */
    @Test
    void anExpiredIncompleteSubscriptionIsStoredAsCanceled() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.updated", """
                "status":"incomplete_expired","items":{"data":[{"price":{"id":"price_premium_monthly"}}]}""");

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.subscription().status()).isEqualTo("canceled");
    }

    @Test
    void anEventThatIsNotAboutASubscriptionIsAcknowledgedWithNoSnapshot() {
        StripePaymentProvider provider = provider(true);
        byte[] body = """
                {"id":"evt_9","type":"checkout.session.completed","created":1758095940,
                 "data":{"object":{"id":"cs_test_1"}}}""".getBytes(StandardCharsets.UTF_8);

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.subscription()).isNull();
        assertThat(event.eventId()).isEqualTo("evt_9");
    }

    @Test
    void aSubscriptionEventWithoutOurUserIdIsRejected() {
        StripePaymentProvider provider = provider(true);
        byte[] body = """
                {"id":"evt_1","type":"customer.subscription.created","created":1758095940,
                 "data":{"object":{"id":"sub_1","customer":"cus_1","status":"active","metadata":{}}}}"""
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> provider.parseAndVerify(body, signedHeaders(body, NOW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user_id");
    }

    @Test
    void aBodyThatDoesNotMatchTheSignatureIsRejected() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.created", "\"status\":\"active\"");
        HttpHeaders headers = signedHeaders(body, NOW);
        byte[] tampered = subscriptionEvent("customer.subscription.created", "\"status\":\"canceled\"");

        assertThatThrownBy(() -> provider.parseAndVerify(tampered, headers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature mismatch");
    }

    /** While a signing secret is rolled Stripe signs with both, and either one matching is enough. */
    @Test
    void anyOneOfSeveralSignaturesMatchingIsEnough() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.created", "\"status\":\"active\"");
        long ts = NOW.getEpochSecond();
        HttpHeaders headers = new HttpHeaders();
        headers.add(StripeSignatureVerifier.SIGNATURE_HEADER,
                "t=" + ts + ",v1=" + "0".repeat(64) + ",v1=" + hmacHex(ts, body) + ",v0=ignored");

        assertThat(provider.parseAndVerify(body, headers).subscription().userId()).isEqualTo(USER_ID);
    }

    @Test
    void aSignatureOlderThanTheToleranceIsRejected() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.created", "\"status\":\"active\"");

        assertThatThrownBy(() -> provider.parseAndVerify(body, signedHeaders(body, NOW.minusSeconds(3600))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the accepted window");
    }

    @Test
    void aMissingSignatureHeaderIsRejected() {
        StripePaymentProvider provider = provider(true);
        byte[] body = subscriptionEvent("customer.subscription.created", "\"status\":\"active\"");

        assertThatThrownBy(() -> provider.parseAndVerify(body, new HttpHeaders()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing Stripe-Signature");
    }

    /** Without a secret nothing can be verified, which is our misconfiguration rather than a bad request. */
    @Test
    void anUnconfiguredWebhookSecretIsOurFault() {
        PaymentsProperties properties = new PaymentsProperties(true, "stripe", null, null,
                new PaymentsProperties.Stripe("sk_test_key", "", PRICE_ID, true, 300));
        StripeSignatureVerifier verifier = new StripeSignatureVerifier(properties);

        assertThatThrownBy(() -> verifier.verify(new byte[0], "t=1,v1=abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET");
    }

    private static byte[] subscriptionEvent(String eventType, String subscriptionFields) {
        return ("""
                {"id":"evt_1","object":"event","type":"%s","created":1758095940,
                 "data":{"object":{"id":"sub_1","object":"subscription","customer":"cus_1",
                   "metadata":{"user_id":"%s"},%s}}}""")
                .formatted(eventType, USER_ID, subscriptionFields)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static HttpHeaders signedHeaders(byte[] body, Instant signedAt) {
        long ts = signedAt.getEpochSecond();
        HttpHeaders headers = new HttpHeaders();
        headers.add(StripeSignatureVerifier.SIGNATURE_HEADER, "t=" + ts + ",v1=" + hmacHex(ts, body));
        return headers;
    }

    private static String hmacHex(long ts, byte[] body) {
        byte[] prefix = (ts + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signed = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, signed, 0, prefix.length);
        System.arraycopy(body, 0, signed, prefix.length, body.length);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signed));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
