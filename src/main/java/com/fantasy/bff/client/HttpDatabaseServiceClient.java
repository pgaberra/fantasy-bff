package com.fantasy.bff.client;

import com.fantasy.bff.model.downstream.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * Real {@link DatabaseServiceClient} that talks to fantasy-db-service over HTTP.
 * Active in every profile except {@code mock} (where the in-memory stub is used).
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
            User user = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/users").queryParam("email", email).build())
                    .retrieve()
                    .body(User.class);
            return Optional.ofNullable(user);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void createUser(String email, String passwordHash) {
        restClient.post()
                .uri("/api/v1/users")
                .body(new CreateUserRequest(email, passwordHash))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean existsByEmail(String email) {
        ExistsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/users/exists").queryParam("email", email).build())
                .retrieve()
                .body(ExistsResponse.class);
        return response != null && response.exists();
    }

    private record CreateUserRequest(String email, String passwordHash) {}

    private record ExistsResponse(boolean exists) {}
}
