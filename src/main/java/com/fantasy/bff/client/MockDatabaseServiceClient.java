package com.fantasy.bff.client;

import com.fantasy.bff.config.MockUserProperties;
import com.fantasy.bff.model.downstream.User;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("mock")
public class MockDatabaseServiceClient implements DatabaseServiceClient {

    private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();
    private final MockUserProperties mockUserProperties;
    private final PasswordEncoder passwordEncoder;

    public MockDatabaseServiceClient(MockUserProperties mockUserProperties, PasswordEncoder passwordEncoder) {
        this.mockUserProperties = mockUserProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    void seedMockUser() {
        createUser(mockUserProperties.email(), passwordEncoder.encode(mockUserProperties.password()));
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        return Optional.ofNullable(usersByEmail.get(email));
    }

    @Override
    public void createUser(String email, String passwordHash) {
        User user = new User(UUID.randomUUID().toString(), email, passwordHash);
        usersByEmail.put(email, user);
    }

    @Override
    public boolean existsByEmail(String email) {
        return usersByEmail.containsKey(email);
    }
}
