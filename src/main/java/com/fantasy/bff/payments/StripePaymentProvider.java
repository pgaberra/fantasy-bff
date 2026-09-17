package com.fantasy.bff.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Stripe Billing as the payment provider, sold through Stripe-hosted Checkout.
 *
 * <p>Unlike Paddle, Stripe hosts the whole checkout page, so the URL a checkout returns is Stripe's
 * own and the web only has to send the browser there. With {@code managed-payments} on, Stripe
 * (through Link) is the merchant of record: it collects and remits the tax and appears as the
 * seller on the buyer's receipt and statement.
 *
 * <p>The user is carried through Stripe in the subscription's {@code metadata}, set at checkout
 * through {@code subscription_data}. Stripe keeps a subscription's metadata for its whole life, so
 * every subscription webhook names the user it belongs to. The checkout session's own metadata
 * records the price it sells, which is how {@link #isCheckoutOpen} tells a checkout for a price no
 * longer sold without a second call for its line items.
 *
 * <p>The API is called over {@link RestClient} rather than through Stripe's Java library, and the
 * webhook body is read as plain JSON. The library deserializes an event only when its pinned API
 * version matches the version the webhook endpoint was created with, and falls silent otherwise;
 * the four fields read here have kept their shape across versions, with the one move handled in
 * {@link #currentPeriodEnd}.
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "stripe")
public class StripePaymentProvider implements PaymentProvider {

    /**
     * The API version every request is made with, so that a change to the account's default in
     * Stripe's dashboard cannot change what this class receives. Managed Payments needs basil or
     * later.
     */
    public static final String API_VERSION = "2025-03-31.basil";

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider.class);
    private static final String USER_ID_KEY = "user_id";
    private static final String PRICE_ID_KEY = "price_id";
    private static final String SUBSCRIPTION_EVENT_PREFIX = "customer.subscription.";

    private final RestClient stripeClient;
    private final ObjectMapper objectMapper;
    private final StripeSignatureVerifier signatureVerifier;
    private final String priceId;
    private final boolean managedPayments;

    public StripePaymentProvider(@Qualifier("stripeApiClient") RestClient stripeClient,
                                 ObjectMapper objectMapper,
                                 StripeSignatureVerifier signatureVerifier,
                                 PaymentsProperties properties) {
        this.stripeClient = stripeClient;
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
        this.priceId = properties.stripe().priceId();
        this.managedPayments = properties.stripe().managedPayments();
    }

    @Override
    public String id() {
        return "stripe";
    }

    /**
     * A subscription checkout for the one Premium price. A verified email is prefilled on the
     * checkout; an unverified one is left out, for the reason {@link CheckoutRequest} gives.
     */
    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        if (!StringUtils.hasText(priceId)) {
            throw new IllegalStateException("STRIPE_PRICE_ID is not configured");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("line_items[0][price]", priceId);
        form.add("line_items[0][quantity]", "1");
        form.add("success_url", request.successUrl());
        form.add("cancel_url", request.cancelUrl());
        form.add("client_reference_id", request.userId());
        form.add("metadata[" + USER_ID_KEY + "]", request.userId());
        form.add("metadata[" + PRICE_ID_KEY + "]", priceId);
        form.add("subscription_data[metadata][" + USER_ID_KEY + "]", request.userId());
        if (managedPayments) {
            form.add("managed_payments[enabled]", "true");
        }
        if (StringUtils.hasText(request.customerEmail())) {
            form.add("customer_email", request.customerEmail());
        }

        JsonNode session = call("create a checkout", () -> stripeClient.post()
                .uri("/v1/checkout/sessions")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));

        String url = path(session, "url");
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("Stripe returned a checkout session with no URL");
        }
        return new CheckoutSession(url, path(session, "id"));
    }

    /**
     * Whether a checkout session can still be paid: {@code open}, selling the price checkouts sell
     * now, with a URL. A completed or expired session is not, and Stripe expires an unpaid one
     * after 24 hours on its own.
     *
     * <p>A lookup that fails answers "not open", logged at ERROR, which opens a new checkout rather
     * than costing the sale; {@link PaddlePaymentProvider#isCheckoutOpen} made the same call.
     */
    @Override
    public boolean isCheckoutOpen(String reference) {
        if (!StringUtils.hasText(reference)) {
            return false;
        }
        try {
            JsonNode session = stripeClient.get()
                    .uri("/v1/checkout/sessions/{sessionId}", reference)
                    .retrieve()
                    .body(JsonNode.class);
            return "open".equals(path(session, "status"))
                    && StringUtils.hasText(priceId)
                    && priceId.equals(path(session, "metadata", PRICE_ID_KEY))
                    && StringUtils.hasText(path(session, "url"));
        } catch (RestClientException e) {
            log.error("Could not read a Stripe checkout session, so a new checkout is opened instead of reusing it", e);
            return false;
        }
    }

    @Override
    public PortalSession createPortalSession(PortalRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", request.providerCustomerId());
        form.add("return_url", request.returnUrl());

        JsonNode session = call("open the billing portal", () -> stripeClient.post()
                .uri("/v1/billing_portal/sessions")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));

        String url = path(session, "url");
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("Stripe returned a billing portal session with no URL");
        }
        return new PortalSession(url);
    }

    @Override
    public WebhookEvent parseAndVerify(byte[] rawBody, HttpHeaders headers) {
        signatureVerifier.verify(rawBody, headers.getFirst(StripeSignatureVerifier.SIGNATURE_HEADER));

        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed Stripe webhook body", e);
        }

        String eventId = text(event.get("id"));
        String eventType = text(event.get("type"));
        Instant occurredAt = epochSeconds(event.get("created"));

        // Anything that is not about a subscription is acknowledged and dropped, for the reason
        // BillingController gives: Stripe retries whatever we answer with an error.
        if (eventType == null || !eventType.startsWith(SUBSCRIPTION_EVENT_PREFIX)) {
            return new WebhookEvent(eventId, occurredAt, null, null);
        }

        JsonNode subscription = event.path("data").get("object");
        if (subscription == null || subscription.isNull()) {
            throw new IllegalArgumentException("Stripe subscription webhook has no data object");
        }

        String userId = path(subscription, "metadata", USER_ID_KEY);
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("Stripe subscription webhook carries no " + USER_ID_KEY);
        }

        String status = status(text(subscription.get("status")));
        SubscriptionSnapshot snapshot = new SubscriptionSnapshot(
                userId,
                text(subscription.get("customer")),
                text(subscription.get("id")),
                path(firstItem(subscription), "price", "id"),
                status,
                currentPeriodEnd(subscription),
                cancelScheduled(subscription));

        return new WebhookEvent(eventId, occurredAt, eventType(eventType, status), snapshot);
    }

    /**
     * Runs a Stripe call and turns an error response into a fault on our side, for the reason
     * {@link PaddlePaymentProvider}'s own {@code call} gives: Stripe's 4xx is about our key, our
     * request or our account, never about what the user asked for.
     */
    private static JsonNode call(String what, Supplier<JsonNode> stripeCall) {
        try {
            return stripeCall.get();
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Stripe refused to " + what, e);
        }
    }

    /**
     * Stripe's event names onto ours. A deletion is the subscription ending. An update that leaves
     * it past due is reported as that, since Stripe has no event of its own for it; every other
     * subscription event (paused, resumed, pending updates, trial ending) is an update.
     */
    private static WebhookEventType eventType(String stripeEventType, String status) {
        return switch (stripeEventType) {
            case "customer.subscription.created" -> WebhookEventType.SUBSCRIPTION_CREATED;
            case "customer.subscription.deleted" -> WebhookEventType.SUBSCRIPTION_CANCELED;
            default -> "past_due".equals(status)
                    ? WebhookEventType.SUBSCRIPTION_PAST_DUE
                    : WebhookEventType.SUBSCRIPTION_UPDATED;
        };
    }

    /**
     * Stripe's statuses are the ones db-service models, bar one: {@code incomplete_expired}, a first
     * payment that never went through within 23 hours, which has ended exactly as a canceled one has.
     */
    private static String status(String stripeStatus) {
        return "incomplete_expired".equals(stripeStatus) ? "canceled" : stripeStatus;
    }

    /**
     * Since API version basil the billing period lives on each subscription item rather than on the
     * subscription. The webhook arrives in the version its endpoint was created with, which need not
     * be {@link #API_VERSION}, so the old place is read when the new one is absent.
     */
    private static Instant currentPeriodEnd(JsonNode subscription) {
        Instant itemEnd = epochSeconds(firstItem(subscription) == null ? null
                : firstItem(subscription).get("current_period_end"));
        return itemEnd != null ? itemEnd : epochSeconds(subscription.get("current_period_end"));
    }

    /**
     * A cancellation at the end of the period shows as {@code cancel_at_period_end}, or, when set
     * through the billing portal on newer API versions, as a {@code cancel_at} date with that flag
     * left false. Either one means the subscription is ending.
     */
    private static boolean cancelScheduled(JsonNode subscription) {
        JsonNode atPeriodEnd = subscription.get("cancel_at_period_end");
        JsonNode cancelAt = subscription.get("cancel_at");
        return (atPeriodEnd != null && atPeriodEnd.asBoolean())
                || (cancelAt != null && !cancelAt.isNull());
    }

    private static JsonNode firstItem(JsonNode subscription) {
        JsonNode data = subscription.path("items").get("data");
        return data == null || !data.isArray() || data.isEmpty() ? null : data.get(0);
    }

    private static String path(JsonNode node, String... keys) {
        JsonNode current = node;
        for (String key : keys) {
            if (current == null || current.isNull()) {
                return null;
            }
            current = current.get(key);
        }
        return text(current);
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static Instant epochSeconds(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException("Malformed Stripe timestamp: " + node);
        }
        return Instant.ofEpochSecond(node.asLong());
    }
}
