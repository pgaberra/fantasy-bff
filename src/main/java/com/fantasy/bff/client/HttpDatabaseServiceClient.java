package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.AvatarResponse;
import com.fantasy.bff.generated.db.model.CreateEmailVerificationTokenRequest;
import com.fantasy.bff.generated.db.model.CreatePasswordResetTokenRequest;
import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.PlayerIdPair;
import com.fantasy.bff.generated.db.model.PlayerIdRemapRequest;
import com.fantasy.bff.generated.db.model.PlayerIdRemapResponse;
import com.fantasy.bff.generated.db.model.CreateShareRequest;
import com.fantasy.bff.generated.db.model.CopyProjectionRequest;
import com.fantasy.bff.generated.db.model.ImportProjectionRequest;
import com.fantasy.bff.generated.db.model.RenameProjectionRequest;
import com.fantasy.bff.generated.db.model.StartDraftRequest;
import com.fantasy.bff.generated.db.model.CreateUserRequest;
import com.fantasy.bff.generated.db.model.EmailVerificationTokenResponse;
import com.fantasy.bff.generated.db.model.ExistsResponse;
import com.fantasy.bff.generated.db.model.FacebookUserRequest;
import com.fantasy.bff.generated.db.model.GoogleUserRequest;
import com.fantasy.bff.generated.db.model.PasswordResetRequest;
import com.fantasy.bff.generated.db.model.PasswordResetTokenResponse;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSummaryResponse;
import com.fantasy.bff.generated.db.model.SetAvatarRequest;
import com.fantasy.bff.generated.db.model.SetUsernameRequest;
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
import com.fantasy.bff.generated.db.model.UserResponse;
import com.fantasy.bff.generated.db.model.VerifyEmailRequest;
import com.fantasy.bff.model.downstream.Avatar;
import com.fantasy.bff.model.downstream.EmailVerificationToken;
import com.fantasy.bff.model.downstream.PasswordResetToken;
import com.fantasy.bff.model.downstream.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link DatabaseServiceClient} that talks to fantasy-db-service over HTTP.
 *
 * Uses model classes generated from specs/fantasy-db-service-openapi.yaml —
 * if the db-service API changes, update the spec and re-run ./gradlew openApiGenerate.
 */
@Component
public class HttpDatabaseServiceClient implements DatabaseServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpDatabaseServiceClient.class);

    /**
     * How deep a cause chain is walked looking for the {@link IOException} that marks a transport
     * failure. Generous for the four-deep chains actually seen, and bounded so a self-referencing
     * cause cannot spin here.
     */
    private static final int MAX_CAUSE_DEPTH = 10;

    private final RestClient restClient;
    private final RestClient migrationClient;

    /**
     * Moving a whole projection — in either direction — gets its own client, with a timeout sized
     * for the payload rather than for the calls that carry an id and return a row. The summary
     * list and the share-token calls stay on the ordinary client: they are small, and a slow
     * failure there is worse than a fast one. See databaseProjectionClient. When the list did
     * start losing to that ceiling anyway it was given {@link #retryingRead} rather than moved
     * here: a second three-second attempt is not the same concession as a fifteen-second one.
     */
    private final RestClient projectionClient;

    public HttpDatabaseServiceClient(@Qualifier("databaseServiceClient") RestClient restClient,
                                     @Qualifier("databaseMigrationClient") RestClient migrationClient,
                                     @Qualifier("databaseProjectionClient") RestClient projectionClient) {
        this.restClient = restClient;
        this.migrationClient = migrationClient;
        this.projectionClient = projectionClient;
    }

    /**
     * Asks once more for a read db-service did not get back to us in time.
     *
     * <p>{@code services.database.timeout-ms} is three seconds, and Spring's read timeout is a
     * deadline on the whole exchange rather than a gap between bytes: when it elapses the request is
     * cancelled and the response body stream is closed underneath whoever is reading it. So a moment
     * of slowness in db-service — a cold start after a deploy, a GC pause, a pool stall — costs the
     * caller a 502 even when the answer was on its way. Sometimes when it had already arrived: in
     * JAVA-SPRING-BOOT-2Q the array had been deserialised in full and the cancellation landed on
     * Jackson's read-ahead for end-of-input, so a complete response was thrown away.
     *
     * <p>Asking again is cheap, and only sound for a read: these carry no body and change nothing, so
     * a second attempt has no consequence beyond the extra call. The three seconds stay as they are —
     * the reason the summary list is on the ordinary client is that it gives up quickly when
     * db-service is genuinely gone, and two attempts at three seconds still gives up sooner than the
     * fifteen-second projection client would.
     *
     * <p>What counts as worth retrying is deliberately narrow: a transport failure, never a response.
     * A status db-service chose to send is an answer and is rethrown as it is — a 404 included, since
     * {@link #findUserByEmail} reads one — and a body that will not parse for any reason other than
     * the stream going away is a defect worth seeing rather than doubling.
     *
     * @param call the name of the read, for the log line. Pass a literal: this class is excluded
     *             from the CRLF log-injection check (see config/spotbugs/exclude.xml) precisely
     *             because nothing here can come from a request, and anything taken off one would
     *             reach the log unchecked.
     */
    private static <T> T retryingRead(String call, Supplier<T> read) {
        try {
            return read.get();
        } catch (RestClientException e) {
            if (!isTransportFailure(e)) {
                throw e;
            }
            log.warn("db-service read {} failed at the transport level ({}); asking once more",
                    call, e.getClass().getSimpleName());
            return read.get();
        }
    }

    /**
     * Whether nothing was heard back, as opposed to something we did not like. A cancelled request
     * arrives as a {@link org.springframework.web.client.ResourceAccessException} wrapping an
     * {@link IOException}; a cancellation that lands while Jackson is still on the stream arrives as
     * a plain {@code RestClientException} with the same {@code IOException} further down. Both are
     * the one fault, so both are found by looking for the {@code IOException} rather than by
     * matching exception types.
     */
    private static boolean isTransportFailure(RestClientException e) {
        if (e instanceof RestClientResponseException) {
            return false;
        }
        Throwable cause = e;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        try {
            UserResponse response = retryingRead("findUserByEmail", () -> restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/users").queryParam("email", email).build())
                    .retrieve()
                    .body(UserResponse.class));
            return Optional.ofNullable(response)
                    .map(HttpDatabaseServiceClient::toUser);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public User createUser(String email, String passwordHash) {
        CreateUserRequest request = new CreateUserRequest()
                .email(email)
                .passwordHash(passwordHash);
        UserResponse response = restClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(UserResponse.class);
        if (response == null) {
            throw new IllegalStateException("db-service returned no body when creating the user");
        }
        return toUser(response);
    }

    @Override
    public boolean existsByEmail(String email) {
        ExistsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/users/exists").queryParam("email", email).build())
                .retrieve()
                .body(ExistsResponse.class);
        return response != null && Boolean.TRUE.equals(response.getExists());
    }

    @Override
    public ResolvedUser findOrCreateGoogleUser(String email, String googleSub) {
        GoogleUserRequest request = new GoogleUserRequest()
                .email(email)
                .googleSub(googleSub);
        ResponseEntity<UserResponse> response = restClient.post()
                .uri("/api/v1/users/google")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(UserResponse.class);
        return resolvedUser(response, "Google");
    }

    @Override
    public ResolvedUser findOrCreateFacebookUser(String email, String facebookSub) {
        FacebookUserRequest request = new FacebookUserRequest()
                .email(email)
                .facebookSub(facebookSub);
        ResponseEntity<UserResponse> response = restClient.post()
                .uri("/api/v1/users/facebook")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(UserResponse.class);
        return resolvedUser(response, "Facebook");
    }

    /**
     * db-service answers 201 when the call created the account and 200 when it found or linked
     * one; the status is kept because it is the only thing that tells a sign-up from a sign-in.
     */
    private ResolvedUser resolvedUser(ResponseEntity<UserResponse> response, String provider) {
        UserResponse body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("db-service returned no body when resolving the " + provider + " user");
        }
        return new ResolvedUser(toUser(body), response.getStatusCode() == HttpStatus.CREATED);
    }

    @Override
    public Optional<PasswordResetToken> createPasswordResetToken(String email) {
        CreatePasswordResetTokenRequest request = new CreatePasswordResetTokenRequest().email(email);
        ResponseEntity<PasswordResetTokenResponse> response = restClient.post()
                .uri("/api/v1/users/password-reset/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(PasswordResetTokenResponse.class);
        PasswordResetTokenResponse body = response.getBody();
        if (response.getStatusCode().value() == 204 || body == null) {
            return Optional.empty();
        }
        return Optional.of(new PasswordResetToken(body.getToken(), body.getExpiresAt().toInstant()));
    }

    @Override
    public void resetPassword(String token, String passwordHash) {
        PasswordResetRequest request = new PasswordResetRequest().token(token).passwordHash(passwordHash);
        try {
            restClient.post()
                    .uri("/api/v1/users/password-reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("Invalid or expired password reset token");
            }
            throw e;
        }
    }

    @Override
    public Optional<EmailVerificationToken> createEmailVerificationToken(String email) {
        CreateEmailVerificationTokenRequest request = new CreateEmailVerificationTokenRequest().email(email);
        ResponseEntity<EmailVerificationTokenResponse> response = restClient.post()
                .uri("/api/v1/users/email-verification/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(EmailVerificationTokenResponse.class);
        EmailVerificationTokenResponse body = response.getBody();
        if (response.getStatusCode().value() == 204 || body == null) {
            return Optional.empty();
        }
        return Optional.of(new EmailVerificationToken(body.getToken(), body.getExpiresAt().toInstant()));
    }

    @Override
    public void verifyEmail(String token) {
        VerifyEmailRequest request = new VerifyEmailRequest().token(token);
        try {
            restClient.post()
                    .uri("/api/v1/users/email-verification")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("Invalid or expired email verification token");
            }
            throw e;
        }
    }

    @Override
    public User findUserById(UUID userId) {
        UserResponse response = restClient.get()
                .uri("/api/v1/users/{userId}", userId)
                .retrieve()
                .body(UserResponse.class);
        if (response == null) {
            throw new IllegalStateException("db-service returned no body when reading the user");
        }
        return toUser(response);
    }

    @Override
    public void revokeSessions(UUID userId) {
        restClient.post()
                .uri("/api/v1/users/{userId}/sessions/revoke", userId)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public User setUsername(UUID userId, String username) {
        UserResponse response = restClient.put()
                .uri("/api/v1/users/{userId}/username", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SetUsernameRequest().username(username))
                .retrieve()
                .body(UserResponse.class);
        if (response == null) {
            throw new IllegalStateException("db-service returned no body when setting the username");
        }
        return toUser(response);
    }

    @Override
    public Optional<Avatar> findAvatar(UUID userId) {
        try {
            AvatarResponse response = restClient.get()
                    .uri("/api/v1/users/{userId}/avatar", userId)
                    .retrieve()
                    .body(AvatarResponse.class);
            return Optional.ofNullable(response)
                    .map(avatar -> new Avatar(avatar.getContentType(), avatar.getData()));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void setAvatar(UUID userId, Avatar avatar) {
        restClient.put()
                .uri("/api/v1/users/{userId}/avatar", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SetAvatarRequest().contentType(avatar.contentType()).data(avatar.data()))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void deleteAvatar(UUID userId) {
        try {
            restClient.delete()
                    .uri("/api/v1/users/{userId}/avatar", userId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) {
                throw e;
            }
        }
    }

    private static User toUser(UserResponse response) {
        return new User(response.getId(), response.getEmail(), response.getUsername(),
                response.getPasswordHash(), response.getTokenVersion(), response.getEmailVerified());
    }

    @Override
    public List<ProjectionSummaryResponse> listProjections(UUID userId) {
        ProjectionSummaryResponse[] response = retryingRead("listProjections", () -> restClient.get()
                .uri("/api/v1/users/{userId}/projections", userId)
                .retrieve()
                .body(ProjectionSummaryResponse[].class));
        return response == null ? List.of() : List.of(response);
    }

    @Override
    public ProjectionResponse getProjection(UUID userId, UUID projectionId) {
        return projectionClient.get()
                .uri("/api/v1/users/{userId}/projections/{id}", userId, projectionId)
                .retrieve()
                .body(ProjectionResponse.class);
    }

    @Override
    public ProjectionResponse createProjection(UUID userId, CreateProjectionRequest request) {
        return projectionClient.post()
                .uri("/api/v1/users/{userId}/projections", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProjectionResponse.class);
    }

    /**
     * The status is carried out of here rather than dropped: db-service answers 201 for a follow
     * it created and 200 for one the user already held, and that difference is the whole of what
     * tells the page "you are now following this" from "you already were".
     */
    @Override
    public FollowedProjection followShare(UUID userId, ImportProjectionRequest request) {
        ResponseEntity<ProjectionResponse> response = projectionClient.post()
                .uri("/api/v1/users/{userId}/projections/imports", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ProjectionResponse.class);
        return new FollowedProjection(response.getBody(),
                response.getStatusCode() == HttpStatus.CREATED);
    }

    @Override
    public ProjectionResponse copyShare(UUID userId, CopyProjectionRequest request) {
        return projectionClient.post()
                .uri("/api/v1/users/{userId}/projections/copies", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProjectionResponse.class);
    }

    @Override
    public ProjectionResponse startDraft(UUID userId, UUID boardId, StartDraftRequest request) {
        return projectionClient.post()
                .uri("/api/v1/users/{userId}/projections/{id}/drafts", userId, boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProjectionResponse.class);
    }

    @Override
    public ProjectionSummaryResponse renameProjection(UUID userId, UUID id,
                                                      RenameProjectionRequest request) {
        return restClient.put()
                .uri("/api/v1/users/{userId}/projections/{id}/name", userId, id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProjectionSummaryResponse.class);
    }

    @Override
    public ProjectionResponse updateProjection(UUID userId, UUID projectionId, UpdateProjectionRequest request) {
        return projectionClient.put()
                .uri("/api/v1/users/{userId}/projections/{id}", userId, projectionId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProjectionResponse.class);
    }

    @Override
    public void deleteProjection(UUID userId, UUID projectionId) {
        restClient.delete()
                .uri("/api/v1/users/{userId}/projections/{id}", userId, projectionId)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public ShareResponse shareProjection(UUID userId, UUID projectionId, CreateShareRequest request) {
        return restClient.put()
                .uri("/api/v1/users/{userId}/projections/{projectionId}/share", userId, projectionId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ShareResponse.class);
    }

    @Override
    public ShareResponse getProjectionShare(UUID userId, UUID projectionId) {
        return restClient.get()
                .uri("/api/v1/users/{userId}/projections/{projectionId}/share", userId, projectionId)
                .retrieve()
                .body(ShareResponse.class);
    }


    @Override
    public SharedProjectionResponse getSharedProjection(String token) {
        return projectionClient.get()
                .uri("/api/v1/shares/{token}", token)
                .retrieve()
                .body(SharedProjectionResponse.class);
    }

    @Override
    public Optional<Avatar> findSharedProjectionAuthorAvatar(String token) {
        try {
            AvatarResponse response = restClient.get()
                    .uri("/api/v1/shares/{token}/avatar", token)
                    .retrieve()
                    .body(AvatarResponse.class);
            return Optional.ofNullable(response)
                    .map(avatar -> new Avatar(avatar.getContentType(), avatar.getData()));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Optional<SubscriptionResponse> getSubscription(UUID userId) {
        try {
            SubscriptionResponse response = restClient.get()
                    .uri("/api/v1/users/{userId}/subscription", userId)
                    .retrieve()
                    .body(SubscriptionResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public SubscriptionResponse upsertSubscription(UUID userId, UpsertSubscriptionRequest request) {
        return restClient.put()
                .uri("/api/v1/users/{userId}/subscription", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SubscriptionResponse.class);
    }

    @Override
    public Optional<PendingCheckoutResponse> getPendingCheckout(UUID userId) {
        try {
            PendingCheckoutResponse response = restClient.get()
                    .uri("/api/v1/users/{userId}/pending-checkout", userId)
                    .retrieve()
                    .body(PendingCheckoutResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public boolean replacePendingCheckout(UUID userId, ReplacePendingCheckoutRequest request) {
        try {
            restClient.put()
                    .uri("/api/v1/users/{userId}/pending-checkout", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public PremiumEntitlementResponse getPremiumEntitlement(UUID userId) {
        return restClient.get()
                .uri("/api/v1/users/{userId}/premium", userId)
                .retrieve()
                .body(PremiumEntitlementResponse.class);
    }

    @Override
    public List<PremiumCustomerResponse> listPremiumCustomers() {
        PremiumCustomerResponse[] customers = restClient.get()
                .uri("/api/v1/premium/customers")
                .retrieve()
                .body(PremiumCustomerResponse[].class);
        return customers == null ? List.of() : List.of(customers);
    }

    @Override
    public PremiumGrantResponse grantPremium(UUID userId, GrantPremiumRequest request) {
        return restClient.post()
                .uri("/api/v1/users/{userId}/premium/grants", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PremiumGrantResponse.class);
    }

    @Override
    public void revokePremiumGrants(UUID userId) {
        restClient.delete()
                .uri("/api/v1/users/{userId}/premium/grants", userId)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public PlayerIdRemapResponse remapPlayerIds(List<PlayerIdPair> mappings, boolean dryRun,
                                                com.fantasy.bff.service.PlayerIdSpace from,
                                                com.fantasy.bff.service.PlayerIdSpace to) {
        return migrationClient.post()
                .uri("/api/v1/admin/player-ids/remap")
                .body(new PlayerIdRemapRequest().mappings(mappings).dryRun(dryRun)
                        .from(PlayerIdRemapRequest.FromEnum.valueOf(from.name()))
                        .to(PlayerIdRemapRequest.ToEnum.valueOf(to.name())))
                .retrieve()
                .body(PlayerIdRemapResponse.class);
    }
}
