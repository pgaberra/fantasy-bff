package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.CreateUserRequest;
import com.fantasy.bff.generated.db.model.ExistsResponse;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSummaryResponse;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.fantasy.bff.generated.db.model.UserResponse;
import com.fantasy.bff.model.downstream.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
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
                    .map(r -> new User(r.getId(), r.getEmail(), r.getPasswordHash()));
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
        return new User(response.getId(), response.getEmail(), response.getPasswordHash());
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
}
