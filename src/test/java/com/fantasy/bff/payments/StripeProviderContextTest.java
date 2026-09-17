package com.fantasy.bff.payments;

import com.fantasy.bff.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole context the way a deployment does, with Stripe selected. Unit tests build the
 * Stripe classes with {@code new}, and {@link PaddleProviderContextTest} says what that once let
 * through to a deploy.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "payments.enabled=true",
        "payments.provider=stripe",
        "payments.stripe.api-key=sk_test_key",
        "payments.stripe.webhook-secret=whsec_test",
        "payments.stripe.price-id=price_test"
})
class StripeProviderContextTest extends BaseIntegrationTest {

    @Autowired
    private PaymentProvider paymentProvider;

    @Autowired
    private StripeSignatureVerifier signatureVerifier;

    @Autowired
    private PaymentsProperties paymentsProperties;

    @Test
    void stripeIsTheProviderTheContextWiresUp() {
        assertThat(paymentProvider).isInstanceOf(StripePaymentProvider.class);
        assertThat(paymentProvider.id()).isEqualTo("stripe");
        assertThat(signatureVerifier).isNotNull();
    }

    /** Left unset, Stripe is the merchant of record: we never become the seller by omission. */
    @Test
    void managedPaymentsIsOnUnlessTurnedOff() {
        assertThat(paymentsProperties.stripe().managedPayments()).isTrue();
    }
}
