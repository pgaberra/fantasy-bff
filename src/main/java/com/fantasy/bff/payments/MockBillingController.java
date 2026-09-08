package com.fantasy.bff.payments;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Server-rendered stub standing in for a real provider's hosted checkout + management portal, so
 * the whole subscription lifecycle works locally in test mode. The confirm/cancel actions self-POST
 * a signed webhook to {@code /api/v1/billing/webhook}, then 302 back to the web app. The stub pages
 * are constant HTML — the token travels via the browser URL and is wired into the action links by
 * inline JS, so no request data is reflected server-side. Hidden from the OpenAPI spec, and every
 * handler returns 404 unless {@code payments.enabled} — so while payments are off (e.g. prod today)
 * nothing under {@code /api/v1/billing/mock/**} does anything or serves a stub page.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/billing/mock")
@ConditionalOnProperty(name = "payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockBillingController {

    private static final String PRICE_ID = "price_mock_monthly";
    private static final long PERIOD_DAYS = 30;

    private static final String CHECKOUT_PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Mock checkout</title></head>
            <body style="font-family: system-ui, sans-serif; max-width: 32rem; margin: 4rem auto; padding: 0 1rem;">
            <h1>Mock checkout</h1>
            <p>Test payment stub - no real charge is made.</p>
            <p><a id="primary">Confirm subscription</a></p>
            <p><a id="secondary">Cancel</a></p>
            <script>
              var t = new URLSearchParams(location.search).get('token') || '';
              var q = '?token=' + encodeURIComponent(t);
              document.getElementById('primary').href = '/api/v1/billing/mock/checkout/confirm' + q;
              document.getElementById('secondary').href = '/api/v1/billing/mock/checkout/cancel' + q;
            </script>
            </body>
            </html>
            """;

    private static final String PORTAL_PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Mock billing portal</title></head>
            <body style="font-family: system-ui, sans-serif; max-width: 32rem; margin: 4rem auto; padding: 0 1rem;">
            <h1>Mock billing portal</h1>
            <p>Test billing portal stub - no real charge is made.</p>
            <p><a id="primary">Cancel subscription</a></p>
            <p><a id="secondary">Reactivate subscription</a></p>
            <script>
              var t = new URLSearchParams(location.search).get('token') || '';
              var q = '?token=' + encodeURIComponent(t);
              document.getElementById('primary').href = '/api/v1/billing/mock/portal/cancel' + q;
              document.getElementById('secondary').href = '/api/v1/billing/mock/portal/reactivate' + q;
            </script>
            </body>
            </html>
            """;

    private final MockBillingCodec codec;
    private final ObjectMapper objectMapper;
    private final PaymentsProperties paymentsProperties;
    private final RestClient selfClient;
    private final String webBaseUrl;

    public MockBillingController(MockBillingCodec codec, ObjectMapper objectMapper,
                                 PaymentsProperties properties,
                                 @Value("${app.web-base-url}") String webBaseUrl) {
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.paymentsProperties = properties;
        this.webBaseUrl = webBaseUrl;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.selfClient = RestClient.builder()
                .baseUrl(properties.mock().selfBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @GetMapping(value = "/checkout", produces = MediaType.TEXT_HTML_VALUE)
    public String checkoutPage() {
        requireEnabled();
        return CHECKOUT_PAGE;
    }

    @GetMapping(value = "/portal", produces = MediaType.TEXT_HTML_VALUE)
    public String portalPage() {
        requireEnabled();
        return PORTAL_PAGE;
    }

    @GetMapping("/checkout/confirm")
    public ResponseEntity<Void> confirmCheckout(@RequestParam String token) {
        requireEnabled();
        return emitAndRedirect(codec.decodeToken(token), WebhookEventType.SUBSCRIPTION_CREATED, "active", false,
                "/premium?checkout=success");
    }

    @GetMapping("/checkout/cancel")
    public ResponseEntity<Void> cancelCheckout(@RequestParam String token) {
        requireEnabled();
        codec.decodeToken(token);
        return redirect("/premium?checkout=cancel");
    }

    @GetMapping("/portal/cancel")
    public ResponseEntity<Void> cancelSubscription(@RequestParam String token) {
        requireEnabled();
        return emitAndRedirect(codec.decodeToken(token), WebhookEventType.SUBSCRIPTION_UPDATED, "active", true,
                "/premium?portal=return");
    }

    @GetMapping("/portal/reactivate")
    public ResponseEntity<Void> reactivateSubscription(@RequestParam String token) {
        requireEnabled();
        return emitAndRedirect(codec.decodeToken(token), WebhookEventType.SUBSCRIPTION_UPDATED, "active", false,
                "/premium?portal=return");
    }

    private void requireEnabled() {
        if (!paymentsProperties.enabled()) {
            throw new NoSuchElementException("Payments are not enabled");
        }
    }

    private ResponseEntity<Void> emitAndRedirect(String userId, WebhookEventType type, String status,
                                                 boolean cancelAtPeriodEnd, String webPath) {
        Instant now = Instant.now();
        SubscriptionSnapshot snapshot = new SubscriptionSnapshot(userId, "cus_mock_" + userId,
                "sub_mock_" + userId, PRICE_ID, status, now.plus(PERIOD_DAYS, ChronoUnit.DAYS), cancelAtPeriodEnd);
        WebhookEvent event = new WebhookEvent(UUID.randomUUID().toString(), now, type, snapshot);
        byte[] body = objectMapper.writeValueAsBytes(event);
        selfClient.post()
                .uri("/api/v1/billing/webhook")
                .header(MockPaymentProvider.SIGNATURE_HEADER, codec.signBody(body))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        return redirect(webPath);
    }

    private ResponseEntity<Void> redirect(String webPath) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(webBaseUrl + webPath)).build();
    }
}
