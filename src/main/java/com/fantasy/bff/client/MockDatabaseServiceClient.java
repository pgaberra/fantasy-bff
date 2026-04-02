package com.fantasy.bff.client;

import com.fantasy.bff.model.downstream.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("mock")
public class MockDatabaseServiceClient implements DatabaseServiceClient {

    private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findUserByEmail(String email) {
        return Optional.ofNullable(usersByEmail.get(email));
    }

    @Override
    public void createUser(String username, String email, String passwordHash) {
        User user = new User(UUID.randomUUID().toString(), username, email, passwordHash);
        usersByEmail.put(email, user);
    }

    @Override
    public boolean existsByEmail(String email) {
        return usersByEmail.containsKey(email);
    }
}
