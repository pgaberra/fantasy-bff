package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.generated.db.model.GrantPremiumRequest;
import com.fantasy.bff.generated.db.model.PremiumCustomerResponse;
import com.fantasy.bff.generated.db.model.PremiumGrantResponse;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin's view of premium: who has it, and giving it to a friend for a while without a
 * payment. Nothing here is reachable without the admin claim, which is the whole point.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPremiumControllerTest extends BaseIntegrationTest {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FRIEND_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private DatabaseServiceClient databaseServiceClient;

    private String adminToken() {
        return jwtTokenValidator.generateToken(ADMIN_ID.toString(), "admin@example.com", true);
    }

    private String userToken() {
        return jwtTokenValidator.generateToken(UUID.randomUUID().toString(), "user@example.com", false);
    }

    private void withAdminAccount() {
        when(databaseServiceClient.findUserById(ADMIN_ID))
                .thenReturn(new User(ADMIN_ID.toString(), "admin@example.com", "admin", null, 0, true));
    }

    private void withFriendAccount() {
        when(databaseServiceClient.findUserByEmail("friend@example.com"))
                .thenReturn(Optional.of(new User(FRIEND_ID.toString(), "friend@example.com", null, null, 0, true)));
    }

    private static String grantBody(String email, int months) {
        return """
                { "email": "%s", "months": %d, "reason": "a friend" }
                """.formatted(email, months);
    }

    @Test
    void customersAreListedForAnAdmin() throws Exception {
        when(databaseServiceClient.listPremiumCustomers()).thenReturn(List.of(
                new PremiumCustomerResponse()
                        .userId(FRIEND_ID.toString())
                        .email("friend@example.com")
                        .source(PremiumCustomerResponse.SourceEnum.GRANT)
                        .cancelAtPeriodEnd(false)
                        .grantedBy("admin@example.com")
                        .grantExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(2))
                        .premiumUntil(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(2))
                        .userCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3))));

        mockMvc.perform(get("/api/v1/admin/premium/customers")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("friend@example.com"))
                .andExpect(jsonPath("$[0].source").value("grant"))
                .andExpect(jsonPath("$[0].grantedBy").value("admin@example.com"));
    }

    @Test
    void customersAreRefusedToAnAccountWithoutTheAdminClaim() throws Exception {
        mockMvc.perform(get("/api/v1/admin/premium/customers")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(databaseServiceClient);
    }

    @Test
    void grantingRecordsTheMonthsAskedForAndWhoGaveThem() throws Exception {
        withAdminAccount();
        withFriendAccount();
        when(databaseServiceClient.grantPremium(eq(FRIEND_ID), any())).thenReturn(
                new PremiumGrantResponse()
                        .id(UUID.randomUUID().toString())
                        .userId(FRIEND_ID.toString())
                        .startsAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(2))
                        .grantedBy("admin@example.com")
                        .reason("a friend")
                        .active(true)
                        .createdAt(OffsetDateTime.now(ZoneOffset.UTC)));

        mockMvc.perform(post("/api/v1/admin/premium/grants")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody("friend@example.com", 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("friend@example.com"))
                .andExpect(jsonPath("$.grantedBy").value("admin@example.com"));

        ArgumentCaptor<GrantPremiumRequest> sent = ArgumentCaptor.forClass(GrantPremiumRequest.class);
        verify(databaseServiceClient).grantPremium(eq(FRIEND_ID), sent.capture());
        assertThat(sent.getValue().getGrantedBy()).isEqualTo("admin@example.com");
        assertThat(sent.getValue().getExpiresAt())
                .isCloseTo(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(2),
                        org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
    }

    @Test
    void grantingToAnUnknownEmailReturns404() throws Exception {
        withAdminAccount();
        when(databaseServiceClient.findUserByEmail("stranger@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/admin/premium/grants")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody("stranger@example.com", 2)))
                .andExpect(status().isNotFound());
    }

    @Test
    void grantingForMoreMonthsThanAllowedIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/premium/grants")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody("friend@example.com", 99)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void grantingIsRefusedToAnAccountWithoutTheAdminClaim() throws Exception {
        mockMvc.perform(post("/api/v1/admin/premium/grants")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody("friend@example.com", 2)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(databaseServiceClient);
    }

    @Test
    void revokingEndsTheGrant() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/premium/grants/{userId}", FRIEND_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());

        verify(databaseServiceClient).revokePremiumGrants(FRIEND_ID);
    }
}
