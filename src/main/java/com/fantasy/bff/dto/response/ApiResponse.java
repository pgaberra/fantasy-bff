package com.fantasy.bff.dto.response;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDto error,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(ErrorDto error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }
}
