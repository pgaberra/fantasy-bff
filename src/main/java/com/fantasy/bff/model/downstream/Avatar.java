package com.fantasy.bff.model.downstream;

public record Avatar(
        String contentType,
        byte[] data
) {}
