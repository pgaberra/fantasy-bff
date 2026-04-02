package com.fantasy.bff.client;

import com.fantasy.bff.model.downstream.User;

import java.util.Optional;

public interface DatabaseServiceClient {

    Optional<User> findUserByEmail(String email);

    void createUser(String username, String email, String passwordHash);

    boolean existsByEmail(String email);
}
