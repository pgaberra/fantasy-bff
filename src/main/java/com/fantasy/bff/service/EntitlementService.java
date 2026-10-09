package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.response.EntitlementsResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * What a user is currently entitled to. db-service answers it in one call, since premium can rest
 * on a paid subscription, on a grant an admin handed out, or on both.
 *
 * <p>Read live from db-service on every ask rather than carried as a JWT claim: a provider
 * webhook can turn premium on or off mid-session, and a token minted before that would go on
 * saying the old thing until it expired.
 */
@Service
public class EntitlementService {

    private final DatabaseServiceClient databaseServiceClient;

    public EntitlementService(DatabaseServiceClient databaseServiceClient) {
        this.databaseServiceClient = databaseServiceClient;
    }

    /** What to report to the user about their own premium access. */
    public EntitlementsResponse entitlements(String userId) {
        return EntitlementsResponse.from(
                databaseServiceClient.getPremiumEntitlement(UUID.fromString(userId)));
    }

    /** Whether this user may use a feature that premium pays for. */
    public boolean hasPremiumAccess(String userId) {
        return entitlements(userId).premium();
    }
}
