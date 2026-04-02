package com.fantasy.bff.model.downstream;

public record User(
        String id,
        String username,
        String email,
        String passwordHash
) {}
