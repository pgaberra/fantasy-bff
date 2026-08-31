package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.response.EntitlementsResponse;
import com.fantasy.bff.payments.PaymentsProperties;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * What a user's subscription currently entitles them to.
 *
 * <p>Read live from db-service on every ask rather than carried as a JWT claim: a provider
 * webhook can turn premium on or off mid-session, and a token minted before that would go on
 * saying the old thing until it expired.
 */
@Service
public class EntitlementService {

    private final DatabaseServiceClient databaseServiceClient;
    private final PaymentsProperties paymentsProperties;

    public EntitlementService(
            DatabaseServiceClient databaseServiceClient, PaymentsProperties paymentsProperties) {
        this.databaseServiceClient = databaseServiceClient;
        this.paymentsProperties = paymentsProperties;
    }

    /**
     * What to report to the user about their own subscription. With payments switched off this
     * answers "no premium" rather than failing, so the web renders the same either way.
     */
    public EntitlementsResponse entitlements(String userId) {
        if (!paymentsProperties.enabled()) {
            return EntitlementsResponse.none();
        }
        return databaseServiceClient.getSubscription(UUID.fromString(userId))
                .map(EntitlementsResponse::from)
                .orElseGet(EntitlementsResponse::none);
    }

    /**
     * Whether this user may use a feature that premium pays for. Not the same question as
     * {@link #entitlements}: where premium is not sold, nothing is held back for it. The web's
     * pricing page redirects home while {@code payments.enabled} is off, so gating then would
     * be a door with no handle, and the flag is checked first so the common case never costs a
     * call to db-service.
     */
    public boolean hasPremiumAccess(String userId) {
        return !paymentsProperties.enabled() || entitlements(userId).premium();
    }
}
