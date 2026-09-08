package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.AdminGrantPremiumRequest;
import com.fantasy.bff.dto.response.AdminPremiumCustomerResponse;
import com.fantasy.bff.dto.response.AdminPremiumGrantResponse;
import com.fantasy.bff.generated.db.model.GrantPremiumRequest;
import com.fantasy.bff.generated.db.model.PremiumGrantResponse;
import com.fantasy.bff.model.downstream.User;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The admin's side of premium: who has it, and giving it to someone without a payment.
 *
 * <p>A grant is stored beside the subscription rather than as one, so handing premium out never
 * touches billing state and a provider event never quietly cancels a gift.
 */
@Service
public class AdminPremiumService {

    private final DatabaseServiceClient databaseServiceClient;

    public AdminPremiumService(DatabaseServiceClient databaseServiceClient) {
        this.databaseServiceClient = databaseServiceClient;
    }

    public List<AdminPremiumCustomerResponse> customers() {
        return databaseServiceClient.listPremiumCustomers().stream()
                .map(AdminPremiumCustomerResponse::from)
                .toList();
    }

    public AdminPremiumGrantResponse grant(String adminUserId, AdminGrantPremiumRequest request) {
        User recipient = databaseServiceClient.findUserByEmail(request.email())
                .orElseThrow(() -> new NoSuchElementException("No account with email: " + request.email()));
        String grantedBy = databaseServiceClient.findUserById(UUID.fromString(adminUserId)).email();

        PremiumGrantResponse grant = databaseServiceClient.grantPremium(
                UUID.fromString(recipient.id()),
                new GrantPremiumRequest()
                        .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(request.months()))
                        .grantedBy(grantedBy)
                        .reason(request.reason()));
        return AdminPremiumGrantResponse.from(grant, recipient.email());
    }

    public void revokeGrants(UUID userId) {
        databaseServiceClient.revokePremiumGrants(userId);
    }
}
