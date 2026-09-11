package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.generated.db.model.PremiumEntitlementResponse;
import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import com.fantasy.bff.generated.db.model.UpsertSubscriptionRequest;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.payments.CheckoutRequest;
import com.fantasy.bff.payments.MockBillingCodec;
import com.fantasy.bff.payments.MockPaymentProvider;
import com.fantasy.bff.payments.PaymentProvider;
import com.fantasy.bff.payments.SubscriptionSnapshot;
import com.fantasy.bff.payments.WebhookEvent;
import com.fantasy.bff.payments.WebhookEventType;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "payments.enabled=true",
        "payments.provider=mock",
        "payments.mock.webhook-secret=test-mock-secret",
        "payments.mock.self-base-url=http://localhost:8080"
})
class BillingControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @Autowired
    private MockBillingCodec codec;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DatabaseServiceClient databaseServiceClient;

    @MockitoSpyBean
    private PaymentProvider paymentProvider;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private String token() {
        return jwtTokenValidator.generateToken(USER_ID.toString(), "owner@example.com");
    }

    private void accountWithEmail(boolean emailVerified) {
        when(databaseServiceClient.findUserById(USER_ID))
                .thenReturn(new User(USER_ID.toString(), "owner@example.com", "alex", "hash", 0, emailVerified));
    }

    private CheckoutRequest checkoutRequestSentToTheProvider() {
        ArgumentCaptor<CheckoutRequest> captor = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(paymentProvider).createCheckoutSession(captor.capture());
        return captor.getValue();
    }

    @Test
    void checkoutSession_returnsMockCheckoutUrl() throws Exception {
        accountWithEmail(true);

        mockMvc.perform(post("/api/v1/billing/checkout-session").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value(containsString("/api/v1/billing/mock/checkout")));
    }

    @Test
    void checkoutSession_verifiedEmail_isHandedToTheProvider() throws Exception {
        accountWithEmail(true);

        mockMvc.perform(post("/api/v1/billing/checkout-session").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk());

        CheckoutRequest request = checkoutRequestSentToTheProvider();
        assertThat(request.userId()).isEqualTo(USER_ID.toString());
        assertThat(request.customerEmail()).isEqualTo("owner@example.com");
    }

    /**
     * An unverified address may belong to someone else. Handed to Paddle it would attach that
     * person's customer to this account's subscription, and with it their billing portal.
     */
    @Test
    void checkoutSession_unverifiedEmail_isNotHandedToTheProvider() throws Exception {
        accountWithEmail(false);

        mockMvc.perform(post("/api/v1/billing/checkout-session").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk());

        assertThat(checkoutRequestSentToTheProvider().customerEmail()).isNull();
    }

    @Test
    void mockCheckoutStub_whenEnabled_servesTheStubPage() throws Exception {
        mockMvc.perform(get("/api/v1/billing/mock/checkout"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mock checkout")));
    }

    @Test
    void checkoutSession_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/billing/checkout-session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void entitlements_noSubscription_returnsPremiumFalse() throws Exception {
        when(databaseServiceClient.getPremiumEntitlement(USER_ID)).thenReturn(
                new PremiumEntitlementResponse()
                        .premium(false)
                        .source(PremiumEntitlementResponse.SourceEnum.NONE)
                        .cancelAtPeriodEnd(false));

        mockMvc.perform(get("/api/v1/billing/entitlements").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.status").value("none"));
    }

    @Test
    void entitlements_activeSubscription_returnsPremiumTrue() throws Exception {
        when(databaseServiceClient.getPremiumEntitlement(USER_ID)).thenReturn(
                new PremiumEntitlementResponse()
                        .premium(true)
                        .source(PremiumEntitlementResponse.SourceEnum.SUBSCRIPTION)
                        .subscriptionStatus(PremiumEntitlementResponse.SubscriptionStatusEnum.ACTIVE)
                        .cancelAtPeriodEnd(false)
                        .currentPeriodEnd(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))
                        .premiumUntil(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30)));

        mockMvc.perform(get("/api/v1/billing/entitlements").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(true))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.source").value("subscription"));
    }

    @Test
    void entitlements_grantedPremium_saysItIsAGrant() throws Exception {
        when(databaseServiceClient.getPremiumEntitlement(USER_ID)).thenReturn(
                new PremiumEntitlementResponse()
                        .premium(true)
                        .source(PremiumEntitlementResponse.SourceEnum.GRANT)
                        .cancelAtPeriodEnd(false)
                        .grantExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(2))
                        .premiumUntil(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(2)));

        mockMvc.perform(get("/api/v1/billing/entitlements").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(true))
                .andExpect(jsonPath("$.source").value("grant"))
                .andExpect(jsonPath("$.status").value("none"))
                .andExpect(jsonPath("$.premiumUntil").exists());
    }

    @Test
    void portalSession_noBillingCustomer_returns404() throws Exception {
        when(databaseServiceClient.getSubscription(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/billing/portal-session").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void portalSession_withBillingCustomer_returnsMockPortalUrl() throws Exception {
        when(databaseServiceClient.getSubscription(USER_ID)).thenReturn(Optional.of(new SubscriptionResponse()
                .status(SubscriptionResponse.StatusEnum.ACTIVE)
                .premium(true)
                .cancelAtPeriodEnd(false)
                .provider("mock")
                .providerCustomerId("cus_mock_1")));

        mockMvc.perform(post("/api/v1/billing/portal-session").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portalUrl").value(containsString("/api/v1/billing/mock/portal")));
    }

    @Test
    void webhook_validSignature_upsertsSubscription() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(new WebhookEvent("evt-1", Instant.now(),
                WebhookEventType.SUBSCRIPTION_CREATED,
                new SubscriptionSnapshot(USER_ID.toString(), "cus_1", "sub_1", "price_1", "active",
                        Instant.now().plusSeconds(2_592_000), false)));
        when(databaseServiceClient.upsertSubscription(eq(USER_ID), any())).thenReturn(new SubscriptionResponse());

        mockMvc.perform(post("/api/v1/billing/webhook")
                        .header(MockPaymentProvider.SIGNATURE_HEADER, codec.signBody(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(databaseServiceClient).upsertSubscription(eq(USER_ID), any(UpsertSubscriptionRequest.class));
    }

    @Test
    void webhook_invalidSignature_returns400_andDoesNotPersist() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(new WebhookEvent("evt-1", Instant.now(),
                WebhookEventType.SUBSCRIPTION_CREATED,
                new SubscriptionSnapshot(USER_ID.toString(), "cus_1", "sub_1", "price_1", "active", null, false)));

        mockMvc.perform(post("/api/v1/billing/webhook")
                        .header(MockPaymentProvider.SIGNATURE_HEADER, "not-a-valid-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(databaseServiceClient, never()).upsertSubscription(any(), any());
    }
}
