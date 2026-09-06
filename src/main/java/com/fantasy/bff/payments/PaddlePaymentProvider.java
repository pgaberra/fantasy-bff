package com.fantasy.bff.payments;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paddle Billing as the payment provider.
 *
 * <p>Checkout is a transaction created here and paid on our own site: Paddle hands back a URL of
 * the form {@code <our checkout page>?_ptxn=<transaction id>}, and the page at that URL loads
 * Paddle.js and opens their checkout over it. There is no fully Paddle-hosted checkout page for
 * the web (theirs is for mobile apps), so the URL this returns points back at us. The
 * {@link PaymentProvider} contract allows that: all it promises is somewhere to send the browser.
 *
 * <p>The user is carried through Paddle in the transaction's {@code custom_data}. Paddle copies
 * custom data from a transaction onto the subscription it creates, and from a subscription onto
 * every renewal, so every webhook we receive names the user it belongs to without us having to
 * create and keep a customer record in step first.
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "paddle")
public class PaddlePaymentProvider implements PaymentProvider {

    private static final String USER_ID_KEY = "user_id";

    private final RestClient paddleClient;
    private final ObjectMapper objectMapper;
    private final PaddleSignatureVerifier signatureVerifier;
    private final String priceId;
    private final String checkoutUrl;

    public PaddlePaymentProvider(@Qualifier("paddleApiClient") RestClient paddleClient,
                                 ObjectMapper objectMapper,
                                 PaddleSignatureVerifier signatureVerifier,
                                 PaymentsProperties properties) {
        this.paddleClient = paddleClient;
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
        this.priceId = properties.paddle().priceId();
        this.checkoutUrl = properties.paddle().checkoutUrl();
    }

    @Override
    public String id() {
        return "paddle";
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        if (!StringUtils.hasText(priceId)) {
            throw new IllegalStateException("PADDLE_PRICE_ID is not configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(Map.of("price_id", priceId, "quantity", 1)));
        body.put("custom_data", Map.of(USER_ID_KEY, request.userId()));
        if (StringUtils.hasText(checkoutUrl)) {
            body.put("checkout", Map.of("url", checkoutUrl));
        }

        JsonNode response = paddleClient.post()
                .uri("/transactions")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String url = path(response, "data", "checkout", "url");
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("Paddle returned a transaction with no checkout URL");
        }
        return new CheckoutSession(url);
    }

    @Override
    public PortalSession createPortalSession(PortalRequest request) {
        JsonNode response = paddleClient.post()
                .uri("/customers/{customerId}/portal-sessions", request.providerCustomerId())
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class);

        String url = path(response, "data", "urls", "general", "overview");
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("Paddle returned a portal session with no overview URL");
        }
        return new PortalSession(url);
    }

    @Override
    public WebhookEvent parseAndVerify(byte[] rawBody, HttpHeaders headers) {
        signatureVerifier.verify(rawBody, headers.getFirst(PaddleSignatureVerifier.SIGNATURE_HEADER));

        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed Paddle webhook body", e);
        }

        String eventType = text(event.get("event_type"));
        Instant occurredAt = instant(text(event.get("occurred_at")));

        // Anything that is not a subscription event is acknowledged and ignored: the controller
        // persists nothing for a null snapshot. Paddle retries any event we answer with an error,
        // so failing on a type we simply do not model would have it retried indefinitely.
        if (eventType == null || !eventType.startsWith("subscription.")) {
            return new WebhookEvent(text(event.get("event_id")), occurredAt, null, null);
        }

        JsonNode data = event.get("data");
        if (data == null || data.isNull()) {
            throw new IllegalArgumentException("Paddle subscription webhook has no data");
        }

        String userId = path(data, "custom_data", USER_ID_KEY);
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("Paddle subscription webhook carries no " + USER_ID_KEY);
        }

        SubscriptionSnapshot snapshot = new SubscriptionSnapshot(
                userId,
                text(data.get("customer_id")),
                text(data.get("id")),
                firstPriceId(data),
                text(data.get("status")),
                instant(path(data, "current_billing_period", "ends_at")),
                cancelScheduled(data));

        return new WebhookEvent(text(event.get("event_id")), occurredAt, eventType(eventType), snapshot);
    }

    /**
     * Paddle's event names onto ours. Only the four we model are named; every other subscription
     * event (activated, trialing, paused, resumed, imported) is a change to a subscription we
     * already track, which is what "updated" means here.
     */
    private static WebhookEventType eventType(String paddleEventType) {
        return switch (paddleEventType) {
            case "subscription.created" -> WebhookEventType.SUBSCRIPTION_CREATED;
            case "subscription.canceled" -> WebhookEventType.SUBSCRIPTION_CANCELED;
            case "subscription.past_due" -> WebhookEventType.SUBSCRIPTION_PAST_DUE;
            default -> WebhookEventType.SUBSCRIPTION_UPDATED;
        };
    }

    /**
     * Paddle reports a pending cancellation as a scheduled change rather than a flag, and the
     * same field carries pauses and resumes too, so read the action rather than its presence.
     */
    private static boolean cancelScheduled(JsonNode data) {
        JsonNode scheduledChange = data.get("scheduled_change");
        return scheduledChange != null
                && !scheduledChange.isNull()
                && "cancel".equals(text(scheduledChange.get("action")));
    }

    private static String firstPriceId(JsonNode data) {
        JsonNode items = data.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return null;
        }
        return path(items.get(0), "price", "id");
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

    private static Instant instant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Malformed Paddle timestamp: " + value, e);
        }
    }
}
