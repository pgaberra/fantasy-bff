package com.fantasy.bff.payments;

import com.fantasy.bff.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole context the way a deployment does, with Paddle selected.
 *
 * <p>Every other test either drives the mock provider or builds the Paddle classes with
 * {@code new}, so nothing asked Spring to construct them until a real deploy did. It could not:
 * {@code PaddleSignatureVerifier} has two constructors and no zero-argument one, so Spring gave
 * up looking for a default constructor and the application failed to start. Unit tests were
 * green throughout. This test is the one that fails instead.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "payments.enabled=true",
        "payments.provider=paddle",
        "payments.paddle.api-key=pdl_sdbx_test",
        "payments.paddle.webhook-secret=pdl_ntfset_test",
        "payments.paddle.price-id=pri_test"
})
class PaddleProviderContextTest extends BaseIntegrationTest {

    @Autowired
    private PaymentProvider paymentProvider;

    @Autowired
    private PaddleSignatureVerifier signatureVerifier;

    @Test
    void paddleIsTheProviderTheContextWiresUp() {
        assertThat(paymentProvider).isInstanceOf(PaddlePaymentProvider.class);
        assertThat(paymentProvider.id()).isEqualTo("paddle");
        assertThat(signatureVerifier).isNotNull();
    }
}
