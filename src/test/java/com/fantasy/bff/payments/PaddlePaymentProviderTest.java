package com.fantasy.bff.payments;

import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaddlePaymentProviderTest {

    private static final String SECRET = "pdl_ntfset_secret";
    private static final String USER_ID = "3f1a5b6c-0000-4000-8000-000000000001";
    private static final Instant NOW = Instant.parse("2026-09-06T18:00:00Z");
    private static final String BUYER_EMAIL = "owner+paddle@example.com";
    private static final String CUSTOMER_LOOKUP_URL =
            "https://sandbox-api.paddle.test/customers?email=owner%2Bpaddle%40example.com&status=active";
    private static final String CHECKOUT_RESPONSE = """
            {"data":{"id":"txn_1","checkout":{"url":"https://slapstat.test/pay?_ptxn=txn_1"}}}""";

    private MockRestServiceServer paddleServer;
    private PaddlePaymentProvider provider;

    @BeforeEach
    void setUp() {
        PaymentsProperties properties = new PaymentsProperties(true, "paddle", null,
                new PaymentsProperties.Paddle("pdl_apikey", SECRET, "pri_premium_monthly",
                        "https://slapstat.test/pay", 300));
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sandbox-api.paddle.test");
        paddleServer = MockRestServiceServer.bindTo(builder).build();
        provider = new PaddlePaymentProvider(builder.build(), new ObjectMapper(),
                new PaddleSignatureVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC)), properties);
    }

    private static CheckoutRequest checkoutFor(String customerEmail) {
        return new CheckoutRequest(USER_ID, "https://slapstat.test/premium", "https://slapstat.test/premium",
                customerEmail);
    }

    @Test
    void checkoutSessionCarriesTheUserAndReturnsPaddlesUrl() {
        paddleServer.expect(requestTo("https://sandbox-api.paddle.test/transactions"))
                .andExpect(content().string(containsString(USER_ID)))
                .andExpect(content().string(containsString("pri_premium_monthly")))
                .andExpect(content().string(not(containsString("customer_id"))))
                .andRespond(withSuccess(CHECKOUT_RESPONSE, MediaType.APPLICATION_JSON));

        CheckoutSession session = provider.createCheckoutSession(checkoutFor(null));

        assertThat(session.url()).isEqualTo("https://slapstat.test/pay?_ptxn=txn_1");
        assertThat(session.reference()).isEqualTo("txn_1");
        paddleServer.verify();
    }

    private void expectTransaction(String status, String transactionPriceId) {
        paddleServer.expect(requestTo("https://sandbox-api.paddle.test/transactions/txn_1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":{"id":"txn_1","status":"%s",
                         "items":[{"price":{"id":"%s"},"quantity":1}],
                         "checkout":{"url":"https://slapstat.test/pay?_ptxn=txn_1"}}}"""
                        .formatted(status, transactionPriceId), MediaType.APPLICATION_JSON));
    }

    @Test
    void aReadyTransactionOnTheCurrentPriceCanStillBePaid() {
        expectTransaction("ready", "pri_premium_monthly");

        assertThat(provider.isCheckoutOpen("txn_1")).isTrue();
        paddleServer.verify();
    }

    @Test
    void aDraftTransactionCanStillBePaid() {
        expectTransaction("draft", "pri_premium_monthly");

        assertThat(provider.isCheckoutOpen("txn_1")).isTrue();
    }

    @Test
    void aCompletedTransactionIsNotReused() {
        expectTransaction("completed", "pri_premium_monthly");

        assertThat(provider.isCheckoutOpen("txn_1")).isFalse();
    }

    /** A checkout opened before the price changed would sell the old price, so it is not handed out. */
    @Test
    void aTransactionForAPriceNoLongerSoldIsNotReused() {
        expectTransaction("ready", "pri_old_price");

        assertThat(provider.isCheckoutOpen("txn_1")).isFalse();
    }

    /** Not being able to read it costs the reuse, not the sale: a new checkout opens as before. */
    @Test
    void aTransactionThatCannotBeReadIsNotReused() {
        paddleServer.expect(requestTo("https://sandbox-api.paddle.test/transactions/txn_1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"not_found\"}}"));

        assertThat(provider.isCheckoutOpen("txn_1")).isFalse();
    }

    /** The email goes in the query encoded: a bare "+" would reach Paddle as a space. */
    @Test
    void anExistingPaddleCustomerIsAttachedToTheCheckout() {
        paddleServer.expect(requestTo(CUSTOMER_LOOKUP_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":[{"id":"ctm_existing","email":"owner+paddle@example.com"}]}""",
                        MediaType.APPLICATION_JSON));
        paddleServer.expect(requestTo("https://sandbox-api.paddle.test/transactions"))
                .andExpect(content().string(containsString("\"customer_id\":\"ctm_existing\"")))
                .andRespond(withSuccess(CHECKOUT_RESPONSE, MediaType.APPLICATION_JSON));

        CheckoutSession session = provider.createCheckoutSession(checkoutFor(BUYER_EMAIL));

        assertThat(session.url()).isEqualTo("https://slapstat.test/pay?_ptxn=txn_1");
        paddleServer.verify();
    }

    @Test
    void aFirstTimeBuyerGetsAPaddleCustomerCreated() {
        paddleServer.expect(requestTo(CUSTOMER_LOOKUP_URL))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        paddleServer.expect(requestTo("https://sandbox-api.paddle.test/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString(BUYER_EMAIL)))
                .andRespond(withSuccess("{\"data\":{\"id\":\"ctm_new\"}}", MediaType.APPLICATION_JSON));
        paddleServer.expect(requestTo("https://sandbox-api.paddle.test/transactions"))
                .andExpect(content().string(containsString("\"customer_id\":\"ctm_new\"")))
                .andRespond(withSuccess(CHECKOUT_RESPONSE, MediaType.APPLICATION_JSON));

        provider.createCheckoutSession(checkoutFor(BUYER_EMAIL));

        paddleServer.verify();
    }

    /**
     * The customer only fills in an email the buyer could type themselves, so a key without the
     * customer scopes, or Paddle refusing the lookup, must cost the prefill and not the sale.
     */
    @Test
    void aFailedCustomerLookupStillOpensTheCheckoutWithoutACustomer() {
        paddleServer.expect(requestTo(CUSTOMER_LOOKUP_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"forbidden\"}}"));
        paddleServer.expect(requestTo("https://sandbox-api.paddle.test/transactions"))
                .andExpect(content().string(not(containsString("customer_id"))))
                .andRespond(withSuccess(CHECKOUT_RESPONSE, MediaType.APPLICATION_JSON));

        CheckoutSession session = provider.createCheckoutSession(checkoutFor(BUYER_EMAIL));

        assertThat(session.url()).isEqualTo("https://slapstat.test/pay?_ptxn=txn_1");
        paddleServer.verify();
    }

    @Test
    void portalSessionReturnsTheOverviewLink() {
        paddleServer.expect(requestTo("https://sandbox-api.paddle.test/customers/ctm_1/portal-sessions"))
                .andRespond(withSuccess("""
                        {"data":{"urls":{"general":{"overview":"https://customer-portal.paddle.com/x?token=t"}}}}""",
                        MediaType.APPLICATION_JSON));

        PortalSession session = provider.createPortalSession(
                new PortalRequest(USER_ID, "ctm_1", "https://slapstat.test/premium"));

        assertThat(session.url()).isEqualTo("https://customer-portal.paddle.com/x?token=t");
        paddleServer.verify();
    }

    @Test
    void webhookIsParsedIntoASnapshotForTheUserNamedInCustomData() {
        byte[] body = subscriptionEvent("subscription.created", "active", null);

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.type()).isEqualTo(WebhookEventType.SUBSCRIPTION_CREATED);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-09-06T17:59:00Z"));
        SubscriptionSnapshot snapshot = event.subscription();
        assertThat(snapshot.userId()).isEqualTo(USER_ID);
        assertThat(snapshot.providerCustomerId()).isEqualTo("ctm_1");
        assertThat(snapshot.providerSubscriptionId()).isEqualTo("sub_1");
        assertThat(snapshot.priceId()).isEqualTo("pri_premium_monthly");
        assertThat(snapshot.status()).isEqualTo("active");
        assertThat(snapshot.currentPeriodEnd()).isEqualTo(Instant.parse("2026-10-06T17:59:00Z"));
        assertThat(snapshot.cancelAtPeriodEnd()).isFalse();
    }

    /**
     * Paddle reports a pause with the same {@code scheduled_change} field it uses for a pending
     * cancellation, so reading the field's presence rather than its action would have every
     * paused subscriber shown as cancelling.
     */
    @Test
    void aScheduledPauseIsNotAPendingCancellation() {
        byte[] body = subscriptionEvent("subscription.updated", "active", "pause");

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.subscription().cancelAtPeriodEnd()).isFalse();
    }

    @Test
    void aScheduledCancellationIsReported() {
        byte[] body = subscriptionEvent("subscription.updated", "active", "cancel");

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.subscription().cancelAtPeriodEnd()).isTrue();
    }

    /** A paused subscription arrives as a plain update; the status is what carries the pause. */
    @Test
    void aPauseIsAnUpdateCarryingThePausedStatus() {
        byte[] body = subscriptionEvent("subscription.paused", "paused", null);

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.type()).isEqualTo(WebhookEventType.SUBSCRIPTION_UPDATED);
        assertThat(event.subscription().status()).isEqualTo("paused");
    }

    /**
     * An event we do not model is verified and then dropped, not rejected. Paddle retries
     * anything answered with an error, so throwing here would have it redelivered forever.
     */
    @Test
    void anEventThatIsNotAboutASubscriptionIsAcknowledgedWithNoSnapshot() {
        byte[] body = """
                {"event_id":"evt_9","event_type":"transaction.completed",
                 "occurred_at":"2026-09-06T17:59:00Z","data":{"id":"txn_9"}}""".getBytes(StandardCharsets.UTF_8);

        WebhookEvent event = provider.parseAndVerify(body, signedHeaders(body, NOW));

        assertThat(event.subscription()).isNull();
    }

    @Test
    void aBodyThatDoesNotMatchTheSignatureIsRejected() {
        byte[] body = subscriptionEvent("subscription.created", "active", null);
        HttpHeaders headers = signedHeaders(body, NOW);
        byte[] tampered = subscriptionEvent("subscription.created", "canceled", null);

        assertThatThrownBy(() -> provider.parseAndVerify(tampered, headers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature mismatch");
    }

    @Test
    void aSignatureOlderThanTheToleranceIsRejected() {
        byte[] body = subscriptionEvent("subscription.created", "active", null);
        HttpHeaders stale = signedHeaders(body, NOW.minusSeconds(3600));

        assertThatThrownBy(() -> provider.parseAndVerify(body, stale))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the accepted window");
    }

    @Test
    void aSubscriptionEventWithoutOurUserIdIsRejected() {
        byte[] body = """
                {"event_id":"evt_1","event_type":"subscription.created",
                 "occurred_at":"2026-09-06T17:59:00Z",
                 "data":{"id":"sub_1","customer_id":"ctm_1","status":"active"}}"""
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> provider.parseAndVerify(body, signedHeaders(body, NOW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user_id");
    }

    /**
     * Paddle answers about *our* request, not the user's, so a 400 from them means our key,
     * payload or account configuration is wrong. Relayed as a 400 it would tell the user to
     * retry something that cannot work, and log as an expected client outcome that nothing
     * alerts on. IllegalStateException is what the advice turns into a 502 with an ERROR log.
     */
    @Test
    void aRejectionFromPaddleIsOurFaultNotTheCallers() {
        paddleServer.expect(requestTo("https://sandbox-api.paddle.test/transactions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"code":"transaction_default_checkout_url_not_set",
                                 "detail":"No default payment link has been set for this account."}}"""));

        assertThatThrownBy(() -> provider.createCheckoutSession(checkoutFor(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("create a checkout")
                // The caller is told only that we failed. Paddle's own words, which name our
                // account configuration, reach the log through the cause and go no further.
                .hasMessageNotContaining("transaction_default_checkout_url_not_set")
                .cause()
                .hasMessageContaining("transaction_default_checkout_url_not_set");
    }

    @Test
    void aRejectedPortalSessionIsOurFaultToo() {
        paddleServer.expect(requestTo("https://sandbox-api.paddle.test/customers/ctm_1/portal-sessions"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"forbidden\"}}"));

        assertThatThrownBy(() -> provider.createPortalSession(
                new PortalRequest(USER_ID, "ctm_1", "https://slapstat.test/premium")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("open the billing portal");
    }

    private static byte[] subscriptionEvent(String eventType, String status, String scheduledAction) {
        String scheduledChange = scheduledAction == null
                ? "null"
                : "{\"action\":\"" + scheduledAction + "\",\"effective_at\":\"2026-10-06T17:59:00Z\"}";
        return ("""
                {"event_id":"evt_1","event_type":"%s","occurred_at":"2026-09-06T17:59:00Z",
                 "data":{"id":"sub_1","customer_id":"ctm_1","status":"%s",
                   "current_billing_period":{"starts_at":"2026-09-06T17:59:00Z",
                                             "ends_at":"2026-10-06T17:59:00Z"},
                   "scheduled_change":%s,
                   "items":[{"price":{"id":"pri_premium_monthly"}}],
                   "custom_data":{"user_id":"%s"}}}""")
                .formatted(eventType, status, scheduledChange, USER_ID)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static HttpHeaders signedHeaders(byte[] body, Instant signedAt) {
        long ts = signedAt.getEpochSecond();
        byte[] prefix = (ts + ":").getBytes(StandardCharsets.UTF_8);
        byte[] signed = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, signed, 0, prefix.length);
        System.arraycopy(body, 0, signed, prefix.length, body.length);

        HttpHeaders headers = new HttpHeaders();
        headers.add(PaddleSignatureVerifier.SIGNATURE_HEADER, "ts=" + ts + ";h1=" + hmacHex(signed));
        return headers;
    }

    private static String hmacHex(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
