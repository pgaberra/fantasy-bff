package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.CreateUserRequest;
import com.fantasy.bff.generated.db.model.ExistsResponse;
import com.fantasy.bff.generated.db.model.UserResponse;
import com.fantasy.bff.model.downstream.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * Real {@link DatabaseServiceClient} that talks to fantasy-db-service over HTTP.
 * Active in every profile except {@code mock} (where the in-memory stub is used).
 *
 * Uses model classes generated from specs/fantasy-db-service-openapi.yaml —
 * if the db-service API changes, update the spec and re-run ./gradlew openApiGenerate.
 */
@Component
@Profile("!mock")
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
    public void createUser(String email, String passwordHash) {
        CreateUserRequest request = new CreateUserRequest()
                .email(email)
                .passwordHash(passwordHash);
        restClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean existsByEmail(String email) {
        ExistsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/users/exists").queryParam("email", email).build())
                .retrieve()
                .body(ExistsResponse.class);
        return response != null && Boolean.TRUE.equals(response.getExists());
    }
}
