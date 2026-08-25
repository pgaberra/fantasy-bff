package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.CreateEmailVerificationTokenRequest;
import com.fantasy.bff.generated.db.model.CreatePasswordResetTokenRequest;
import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.CreateShareRequest;
import com.fantasy.bff.generated.db.model.ImportProjectionRequest;
import com.fantasy.bff.generated.db.model.CreateUserRequest;
import com.fantasy.bff.generated.db.model.EmailVerificationTokenResponse;
import com.fantasy.bff.generated.db.model.ExistsResponse;
import com.fantasy.bff.generated.db.model.FacebookUserRequest;
import com.fantasy.bff.generated.db.model.GoogleUserRequest;
import com.fantasy.bff.generated.db.model.PasswordResetRequest;
import com.fantasy.bff.generated.db.model.PasswordResetTokenResponse;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSummaryResponse;
import com.fantasy.bff.generated.db.model.SetUsernameRequest;
import com.fantasy.bff.generated.db.model.ShareResponse;
import com.fantasy.bff.generated.db.model.SharedProjectionResponse;
import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.fantasy.bff.generated.db.model.UpsertSubscriptionRequest;
import com.fantasy.bff.generated.db.model.UserResponse;
import com.fantasy.bff.generated.db.model.VerifyEmailRequest;
import com.fantasy.bff.model.downstream.EmailVerificationToken;
import com.fantasy.bff.model.downstream.PasswordResetToken;
import com.fantasy.bff.model.downstream.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link DatabaseServiceClient} that talks to fantasy-db-service over HTTP.
 *
 * Uses model classes generated from specs/fantasy-db-service-openapi.yaml —
 * if the db-service API changes, update the spec and re-run ./gradlew openApiGenerate.
 */
@Component
public class HttpDatabaseServiceClient implements DatabaseServiceClient {

    private final RestClient restClient;

    public HttpDatabaseServiceClient(@Qualifier("databaseServiceClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        try {
            UserResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/users").queryParam("email", email).build())
                    .retrieve()
                    .body(UserResponse.class);
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
    public User findOrCreateGoogleUser(String email, String googleSub) {
        GoogleUserRequest request = new GoogleUserRequest()
                .email(email)
                .googleSub(googleSub);
        UserResponse response = restClient.post()
                .uri("/api/v1/users/google")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(UserResponse.class);
        if (response == null) {
            throw new IllegalStateException("db-service returned no body when resolving the Google user");
        }
        return toUser(response);
    }

    @Override
    public User findOrCreateFacebookUser(String email, String facebookSub) {
        FacebookUserRequest request = new FacebookUserRequest()
                .email(email)
                .facebookSub(facebookSub);
        UserResponse response = restClient.post()
                .uri("/api/v1/users/facebook")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(UserResponse.class);
        if (response == null) {
            throw new IllegalStateException("db-service returned no body when resolving the Facebook user");
        }
        return toUser(response);
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

    private static User toUser(UserResponse response) {
        return new User(response.getId(), response.getEmail(), response.getUsername(),
                response.getPasswordHash(), response.getTokenVersion(), response.getEmailVerified());
    }

    @Override
    public List<ProjectionSummaryResponse> listProjections(UUID userId) {
        ProjectionSummaryResponse[] response = restClient.get()
                .uri("/api/v1/users/{userId}/projections", userId)
                .retrieve()
                .body(ProjectionSummaryResponse[].class);
        return response == null ? List.of() : List.of(response);
    }

    @Override
    public ProjectionResponse getProjection(UUID userId, UUID projectionId) {
        return restClient.get()
                .uri("/api/v1/users/{userId}/projections/{id}", userId, projectionId)
                .retrieve()
                .body(ProjectionResponse.class);
    }

    @Override
    public ProjectionResponse createProjection(UUID userId, CreateProjectionRequest request) {
        return restClient.post()
                .uri("/api/v1/users/{userId}/projections", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProjectionResponse.class);
    }

    @Override
    public ProjectionResponse importProjection(UUID userId, ImportProjectionRequest request) {
        return restClient.post()
                .uri("/api/v1/users/{userId}/projections/imports", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProjectionResponse.class);
    }

    @Override
    public ProjectionResponse updateProjection(UUID userId, UUID projectionId, UpdateProjectionRequest request) {
        return restClient.put()
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
        return restClient.get()
                .uri("/api/v1/shares/{token}", token)
                .retrieve()
                .body(SharedProjectionResponse.class);
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
}
