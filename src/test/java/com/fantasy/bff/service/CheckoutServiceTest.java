package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.exception.SubscriptionAlreadyLiveException;
import com.fantasy.bff.generated.db.model.PendingCheckoutResponse;
import com.fantasy.bff.generated.db.model.ReplacePendingCheckoutRequest;
import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.payments.CheckoutSession;
import com.fantasy.bff.payments.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckoutServiceTest {

    private static final String USER_ID = "3f1a5b6c-0000-4000-8000-000000000001";
    private static final UUID ACCOUNT_ID = UUID.fromString(USER_ID);

    private final PaymentProvider paymentProvider = mock(PaymentProvider.class);
    private final DatabaseServiceClient databaseServiceClient = mock(DatabaseServiceClient.class);
    private final CheckoutService service =
            new CheckoutService(paymentProvider, databaseServiceClient, "https://slapstat.test");

    @BeforeEach
    void setUp() {
        when(paymentProvider.id()).thenReturn("paddle");
        when(databaseServiceClient.getSubscription(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(databaseServiceClient.getPendingCheckout(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(databaseServiceClient.findUserById(ACCOUNT_ID))
                .thenReturn(new User(USER_ID, "owner@example.com", "alex", "hash", 0, true));
    }

    private static PendingCheckoutResponse pending(String provider, String reference) {
        return new PendingCheckoutResponse()
                .provider(provider)
                .reference(reference)
                .checkoutUrl("https://slapstat.test/pay?_ptxn=" + reference);
    }

    private ReplacePendingCheckoutRequest storedCheckout() {
        ArgumentCaptor<ReplacePendingCheckoutRequest> captor = ArgumentCaptor.forClass(ReplacePendingCheckoutRequest.class);
        verify(databaseServiceClient).replacePendingCheckout(eq(ACCOUNT_ID), captor.capture());
        return captor.getValue();
    }

    @Test
    void refusesAnAccountThatAlreadySubscribes() {
        when(databaseServiceClient.getSubscription(ACCOUNT_ID))
                .thenReturn(Optional.of(new SubscriptionResponse().live(true).premium(true)));

        assertThatThrownBy(() -> service.checkoutUrlFor(USER_ID)).isInstanceOf(SubscriptionAlreadyLiveException.class);
        verify(paymentProvider, never()).createCheckoutSession(any());
    }

    @Test
    void opensAndStoresAnAccountsFirstCheckout() {
        when(paymentProvider.createCheckoutSession(any()))
                .thenReturn(new CheckoutSession("https://slapstat.test/pay?_ptxn=txn_new", "txn_new"));
        when(databaseServiceClient.replacePendingCheckout(eq(ACCOUNT_ID), any())).thenReturn(true);

        String url = service.checkoutUrlFor(USER_ID);

        assertThat(url).isEqualTo("https://slapstat.test/pay?_ptxn=txn_new");
        ReplacePendingCheckoutRequest stored = storedCheckout();
        assertThat(stored.getReference()).isEqualTo("txn_new");
        assertThat(stored.getProvider()).isEqualTo("paddle");
        assertThat(stored.getReplacesReference()).isNull();
    }

    /** The race this exists for: a second tab gets the checkout the first is paying, not a new one. */
    @Test
    void handsASecondTabTheCheckoutTheFirstIsStillPaying() {
        when(databaseServiceClient.getPendingCheckout(ACCOUNT_ID)).thenReturn(Optional.of(pending("paddle", "txn_1")));
        when(paymentProvider.isCheckoutOpen("txn_1")).thenReturn(true);

        String url = service.checkoutUrlFor(USER_ID);

        assertThat(url).isEqualTo("https://slapstat.test/pay?_ptxn=txn_1");
        verify(paymentProvider, never()).createCheckoutSession(any());
    }

    @Test
    void replacesACheckoutThatCanNoLongerBePaid() {
        when(databaseServiceClient.getPendingCheckout(ACCOUNT_ID)).thenReturn(Optional.of(pending("paddle", "txn_1")));
        when(paymentProvider.isCheckoutOpen("txn_1")).thenReturn(false);
        when(paymentProvider.createCheckoutSession(any()))
                .thenReturn(new CheckoutSession("https://slapstat.test/pay?_ptxn=txn_2", "txn_2"));
        when(databaseServiceClient.replacePendingCheckout(eq(ACCOUNT_ID), any())).thenReturn(true);

        String url = service.checkoutUrlFor(USER_ID);

        assertThat(url).isEqualTo("https://slapstat.test/pay?_ptxn=txn_2");
        assertThat(storedCheckout().getReplacesReference()).isEqualTo("txn_1");
    }

    /**
     * Two tabs arrived together: both found nothing to reuse and both opened a checkout. This one
     * lost the store, so it hands out the winner's, and both tabs end up on one transaction.
     */
    @Test
    void handsOutTheWinnersCheckoutWhenAnotherRequestStoredFirst() {
        when(databaseServiceClient.getPendingCheckout(ACCOUNT_ID))
                .thenReturn(Optional.empty(), Optional.of(pending("paddle", "txn_winner")));
        when(paymentProvider.createCheckoutSession(any()))
                .thenReturn(new CheckoutSession("https://slapstat.test/pay?_ptxn=txn_mine", "txn_mine"));
        when(databaseServiceClient.replacePendingCheckout(eq(ACCOUNT_ID), any())).thenReturn(false);
        when(paymentProvider.isCheckoutOpen("txn_winner")).thenReturn(true);

        String url = service.checkoutUrlFor(USER_ID);

        assertThat(url).isEqualTo("https://slapstat.test/pay?_ptxn=txn_winner");
    }

    /** Switching provider must not send anyone to a checkout the new provider cannot read. */
    @Test
    void doesNotReuseACheckoutFromAnotherProvider() {
        when(databaseServiceClient.getPendingCheckout(ACCOUNT_ID)).thenReturn(Optional.of(pending("mock", "tok_1")));
        when(paymentProvider.createCheckoutSession(any()))
                .thenReturn(new CheckoutSession("https://slapstat.test/pay?_ptxn=txn_2", "txn_2"));
        when(databaseServiceClient.replacePendingCheckout(eq(ACCOUNT_ID), any())).thenReturn(true);

        String url = service.checkoutUrlFor(USER_ID);

        assertThat(url).isEqualTo("https://slapstat.test/pay?_ptxn=txn_2");
        verify(paymentProvider, never()).isCheckoutOpen(anyString());
    }
}
