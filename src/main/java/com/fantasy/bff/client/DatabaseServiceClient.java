package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.PlayerIdPair;
import com.fantasy.bff.generated.db.model.PlayerIdRemapResponse;
import com.fantasy.bff.generated.db.model.CreateShareRequest;
import com.fantasy.bff.generated.db.model.ImportProjectionRequest;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSummaryResponse;
import com.fantasy.bff.generated.db.model.ShareResponse;
import com.fantasy.bff.generated.db.model.SharedProjectionResponse;
import com.fantasy.bff.generated.db.model.GrantPremiumRequest;
import com.fantasy.bff.generated.db.model.PremiumCustomerResponse;
import com.fantasy.bff.generated.db.model.PremiumEntitlementResponse;
import com.fantasy.bff.generated.db.model.PremiumGrantResponse;
import com.fantasy.bff.generated.db.model.PendingCheckoutResponse;
import com.fantasy.bff.generated.db.model.ReplacePendingCheckoutRequest;
import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.fantasy.bff.generated.db.model.UpsertSubscriptionRequest;
import com.fantasy.bff.model.downstream.Avatar;
import com.fantasy.bff.model.downstream.EmailVerificationToken;
import com.fantasy.bff.model.downstream.PasswordResetToken;
import com.fantasy.bff.model.downstream.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatabaseServiceClient {

    Optional<User> findUserByEmail(String email);

    User findUserById(UUID userId);

    /** Ends every session the account holds: refresh tokens issued before stop being accepted. */
    void revokeSessions(UUID userId);

    /** Sets the account's public name. Throws when another account already holds it (409). */
    User setUsername(UUID userId, String username);

    /** The account's profile picture, or empty when it has none. */
    Optional<Avatar> findAvatar(UUID userId);

    /** Replaces the account's profile picture. The bytes are stored and served as given. */
    void setAvatar(UUID userId, Avatar avatar);

    /** Removes the account's profile picture; removing one that is not there is not an error. */
    void deleteAvatar(UUID userId);

    User createUser(String email, String passwordHash);

    boolean existsByEmail(String email);

    /**
     * Resolves the account for a verified Google identity — finds it by subject,
     * links it to an existing same-email account, or creates a password-less user.
     */
    User findOrCreateGoogleUser(String email, String googleSub);

    /**
     * Resolves the account for a verified Facebook identity — finds it by subject,
     * links it to an existing same-email account, or creates a password-less user.
     */
    User findOrCreateFacebookUser(String email, String facebookSub);

    /**
     * Issues a single-use password reset token for the account with this email, or empty
     * when there is no resettable account (unknown email or a Google-only account).
     */
    Optional<PasswordResetToken> createPasswordResetToken(String email);

    /**
     * Consumes a reset token and sets the account's new password hash. Throws
     * {@link IllegalArgumentException} when the token is invalid, expired, or already used.
     */
    void resetPassword(String token, String passwordHash);

    /**
     * Issues a single-use email-verification token for the account with this email, or empty
     * when there is nothing to verify (unknown email or an already-verified account).
     */
    Optional<EmailVerificationToken> createEmailVerificationToken(String email);

    /**
     * Consumes a verification token and marks the account's email verified. Throws
     * {@link IllegalArgumentException} when the token is invalid, expired, or already used.
     */
    void verifyEmail(String token);

    List<ProjectionSummaryResponse> listProjections(UUID userId);

    ProjectionResponse getProjection(UUID userId, UUID projectionId);

    ProjectionResponse createProjection(UUID userId, CreateProjectionRequest request);

    ProjectionResponse importProjection(UUID userId, ImportProjectionRequest request);

    ProjectionResponse updateProjection(UUID userId, UUID projectionId, UpdateProjectionRequest request);

    void deleteProjection(UUID userId, UUID projectionId);

    ShareResponse shareProjection(UUID userId, UUID projectionId, CreateShareRequest request);

    ShareResponse getProjectionShare(UUID userId, UUID projectionId);


    SharedProjectionResponse getSharedProjection(String token);

    Optional<SubscriptionResponse> getSubscription(UUID userId);

    SubscriptionResponse upsertSubscription(UUID userId, UpsertSubscriptionRequest request);

    /** The checkout the user has open with the payment provider, if any. */
    Optional<PendingCheckoutResponse> getPendingCheckout(UUID userId);

    /**
     * Stores the user's open checkout by compare-and-set on {@code replacesReference}. Returns
     * false, rather than throwing, when db-service refuses it (409) because another request stored
     * a checkout since this one read it: the caller should read that one and use it.
     */
    boolean replacePendingCheckout(UUID userId, ReplacePendingCheckoutRequest request);

    PremiumEntitlementResponse getPremiumEntitlement(UUID userId);

    List<PremiumCustomerResponse> listPremiumCustomers();

    PremiumGrantResponse grantPremium(UUID userId, GrantPremiumRequest request);

    void revokePremiumGrants(UUID userId);

    /**
     * Rewrites the player ids in every stored projection and share keyed by {@code from} into
     * {@code to}'s numbering. A dry run reports what would change and writes nothing.
     */
    PlayerIdRemapResponse remapPlayerIds(List<PlayerIdPair> mappings, boolean dryRun,
                                         com.fantasy.bff.service.PlayerIdSpace from,
                                         com.fantasy.bff.service.PlayerIdSpace to);
}
