package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.CreateShareRequest;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSummaryResponse;
import com.fantasy.bff.generated.db.model.ShareResponse;
import com.fantasy.bff.generated.db.model.SharedProjectionResponse;
import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.fantasy.bff.generated.db.model.UpsertSubscriptionRequest;
import com.fantasy.bff.model.downstream.EmailVerificationToken;
import com.fantasy.bff.model.downstream.PasswordResetToken;
import com.fantasy.bff.model.downstream.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatabaseServiceClient {

    Optional<User> findUserByEmail(String email);

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

    ProjectionResponse updateProjection(UUID userId, UUID projectionId, UpdateProjectionRequest request);

    void deleteProjection(UUID userId, UUID projectionId);

    ShareResponse shareProjection(UUID userId, UUID projectionId, CreateShareRequest request);

    ShareResponse getProjectionShare(UUID userId, UUID projectionId);

    void unshareProjection(UUID userId, UUID projectionId);

    SharedProjectionResponse getSharedProjection(String token);

    Optional<SubscriptionResponse> getSubscription(UUID userId);

    SubscriptionResponse upsertSubscription(UUID userId, UpsertSubscriptionRequest request);
}
