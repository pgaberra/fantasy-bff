package com.fantasy.bff.model.downstream;

public record User(
        String id,
        String email,
        String passwordHash
) {}
