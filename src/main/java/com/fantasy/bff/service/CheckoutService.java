package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.exception.SubscriptionAlreadyLiveException;
import com.fantasy.bff.generated.db.model.PendingCheckoutResponse;
import com.fantasy.bff.generated.db.model.ReplacePendingCheckoutRequest;
import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.payments.CheckoutRequest;
import com.fantasy.bff.payments.CheckoutSession;
import com.fantasy.bff.payments.PaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Where an account is sent to pay for Premium, such that it can pay at most once.
 *
 * <p>Two things could otherwise take a second payment. An account that already subscribes is
 * refused outright. An account that is still paying for its first checkout, say in another tab,
 * is handed the same checkout again instead of a new one: the provider lets a transaction be paid
 * once, so a second tab can open it but not pay it twice.
 *
 * <p>The open checkout is stored in db-service by compare-and-set. Two requests that arrive
 * together both find no reusable checkout and both open one, but only the first to store it
 * wins; the other reads the winner and hands out that one, leaving its own unpaid.
 */
@Service
public class CheckoutService {

    private final PaymentProvider paymentProvider;
    private final DatabaseServiceClient databaseServiceClient;
    private final String webBaseUrl;

    public CheckoutService(PaymentProvider paymentProvider, DatabaseServiceClient databaseServiceClient,
                           @Value("${app.web-base-url}") String webBaseUrl) {
        this.paymentProvider = paymentProvider;
        this.databaseServiceClient = databaseServiceClient;
        this.webBaseUrl = webBaseUrl;
    }

    /** The checkout URL to send this account to. */
    public String checkoutUrlFor(String userId) {
        UUID accountId = UUID.fromString(userId);
        refuseIfAlreadySubscribed(accountId);

        Optional<PendingCheckoutResponse> pending = databaseServiceClient.getPendingCheckout(accountId);
        Optional<String> reusable = pending.filter(this::stillPayable).map(PendingCheckoutResponse::getCheckoutUrl);
        if (reusable.isPresent()) {
            return reusable.get();
        }

        CheckoutSession session = paymentProvider.createCheckoutSession(checkoutRequestFor(accountId, userId));
        ReplacePendingCheckoutRequest store = new ReplacePendingCheckoutRequest()
                .provider(paymentProvider.id())
                .reference(session.reference())
                .checkoutUrl(session.url())
                .replacesReference(pending.map(PendingCheckoutResponse::getReference).orElse(null));
        if (databaseServiceClient.replacePendingCheckout(accountId, store)) {
            return session.url();
        }
        // Another request stored a checkout between our read and our store. Use that one, so both
        // tabs pay the same transaction. Should the winner already be unpayable, which would take a
        // payment completing within these milliseconds, ours is still a valid checkout to hand out.
        return databaseServiceClient.getPendingCheckout(accountId)
                .filter(this::stillPayable)
                .map(PendingCheckoutResponse::getCheckoutUrl)
                .orElse(session.url());
    }

    /**
     * One live subscription per account, and db-service is where "live" is defined. A checkout
     * for an account that has one would take a second payment for the Premium it already has.
     */
    private void refuseIfAlreadySubscribed(UUID accountId) {
        boolean alreadySubscribed = databaseServiceClient.getSubscription(accountId)
                .map(SubscriptionResponse::getLive)
                .filter(Boolean.TRUE::equals)
                .isPresent();
        if (alreadySubscribed) {
            throw new SubscriptionAlreadyLiveException("This account already has a live subscription");
        }
    }

    private boolean stillPayable(PendingCheckoutResponse checkout) {
        return paymentProvider.id().equals(checkout.getProvider())
                && paymentProvider.isCheckoutOpen(checkout.getReference());
    }

    private CheckoutRequest checkoutRequestFor(UUID accountId, String userId) {
        User user = databaseServiceClient.findUserById(accountId);
        // Only a verified address may name the buyer at the provider; CheckoutRequest says why.
        String customerEmail = user.emailVerified() ? user.email() : null;
        return new CheckoutRequest(userId, webBaseUrl + "/premium?checkout=success",
                webBaseUrl + "/premium?checkout=cancel", customerEmail);
    }
}
