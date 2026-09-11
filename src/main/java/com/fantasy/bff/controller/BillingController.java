package com.fantasy.bff.controller;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.response.CheckoutUrlResponse;
import com.fantasy.bff.dto.response.EntitlementsResponse;
import com.fantasy.bff.dto.response.PortalUrlResponse;
import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import com.fantasy.bff.generated.db.model.UpsertSubscriptionRequest;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.payments.CheckoutRequest;
import com.fantasy.bff.payments.CheckoutSession;
import com.fantasy.bff.payments.PaymentProvider;
import com.fantasy.bff.payments.PaymentsProperties;
import com.fantasy.bff.payments.PortalRequest;
import com.fantasy.bff.payments.PortalSession;
import com.fantasy.bff.payments.SubscriptionSnapshot;
import com.fantasy.bff.payments.WebhookEvent;
import com.fantasy.bff.service.EntitlementService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Subscription checkout, customer portal, entitlements and the provider webhook. Billing state
 * lives in db-service (the BFF is stateless); the {@link PaymentProvider} abstraction keeps this
 * controller provider-agnostic. Every mutating endpoint is inert (404) unless {@code payments.enabled}.
 */
@Tag(name = "Billing", description = "Subscription checkout, customer portal and premium entitlements")
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final PaymentProvider paymentProvider;
    private final DatabaseServiceClient databaseServiceClient;
    private final EntitlementService entitlementService;
    private final PaymentsProperties paymentsProperties;
    private final String webBaseUrl;

    public BillingController(PaymentProvider paymentProvider, DatabaseServiceClient databaseServiceClient,
                             EntitlementService entitlementService, PaymentsProperties paymentsProperties,
                             @Value("${app.web-base-url}") String webBaseUrl) {
        this.paymentProvider = paymentProvider;
        this.databaseServiceClient = databaseServiceClient;
        this.entitlementService = entitlementService;
        this.paymentsProperties = paymentsProperties;
        this.webBaseUrl = webBaseUrl;
    }

    @Operation(summary = "Start a checkout session to subscribe",
            description = "Returns the provider-hosted checkout URL the web app should redirect the browser to.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checkout URL created"),
            @ApiResponse(responseCode = "404", description = "Payments are not enabled")
    })
    @PostMapping("/checkout-session")
    public CheckoutUrlResponse checkoutSession(@AuthenticationPrincipal String userId) {
        requireEnabled();
        User user = databaseServiceClient.findUserById(UUID.fromString(userId));
        // Only a verified address may name the buyer at the provider; CheckoutRequest says why.
        String customerEmail = user.emailVerified() ? user.email() : null;
        CheckoutSession session = paymentProvider.createCheckoutSession(new CheckoutRequest(
                userId, webBaseUrl + "/premium?checkout=success", webBaseUrl + "/premium?checkout=cancel",
                customerEmail));
        return new CheckoutUrlResponse(session.url());
    }

    @Operation(summary = "Open the billing management portal",
            description = "Returns the provider-hosted customer-portal URL for managing or cancelling the subscription.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Portal URL created"),
            @ApiResponse(responseCode = "404", description = "Payments not enabled, or the user has no billing customer")
    })
    @PostMapping("/portal-session")
    public PortalUrlResponse portalSession(@AuthenticationPrincipal String userId) {
        requireEnabled();
        String customerId = databaseServiceClient.getSubscription(UUID.fromString(userId))
                .map(SubscriptionResponse::getProviderCustomerId)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new NoSuchElementException("No billing customer for user"));
        PortalSession session = paymentProvider.createPortalSession(
                new PortalRequest(userId, customerId, webBaseUrl + "/premium?portal=return"));
        return new PortalUrlResponse(session.url());
    }

    @Operation(summary = "The current user's premium entitlement",
            description = "Live read of whether the user's subscription currently grants premium access.")
    @ApiResponse(responseCode = "200", description = "Entitlement returned")
    @GetMapping("/entitlements")
    public EntitlementsResponse entitlements(@AuthenticationPrincipal String userId) {
        return entitlementService.entitlements(userId);
    }

    @Hidden
    @PostMapping("/webhook")
    public void webhook(@RequestBody byte[] rawBody, @RequestHeader HttpHeaders headers) {
        requireEnabled();
        WebhookEvent event = paymentProvider.parseAndVerify(rawBody, headers);
        SubscriptionSnapshot snapshot = event.subscription();
        // A verified event that says nothing about a subscription is acknowledged and dropped.
        // Providers let you subscribe to more than we model, and they retry anything we answer
        // with an error, so a 200 here is what stops an uninteresting event coming back forever.
        if (snapshot == null) {
            return;
        }
        UpsertSubscriptionRequest request = new UpsertSubscriptionRequest()
                .provider(paymentProvider.id())
                .providerCustomerId(snapshot.providerCustomerId())
                .providerSubscriptionId(snapshot.providerSubscriptionId())
                .priceId(snapshot.priceId())
                .status(UpsertSubscriptionRequest.StatusEnum.fromValue(snapshot.status()))
                .currentPeriodEnd(toOffset(snapshot.currentPeriodEnd()))
                .cancelAtPeriodEnd(snapshot.cancelAtPeriodEnd())
                .eventAt(toOffset(event.occurredAt()));
        databaseServiceClient.upsertSubscription(UUID.fromString(snapshot.userId()), request);
    }

    private void requireEnabled() {
        if (!paymentsProperties.enabled()) {
            throw new NoSuchElementException("Payments are not enabled");
        }
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
