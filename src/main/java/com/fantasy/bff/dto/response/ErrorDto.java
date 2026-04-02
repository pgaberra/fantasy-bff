package com.fantasy.bff.dto.response;

import java.util.Map;

public record ErrorDto(
        String code,
        String message,
        Map<String, Object> details
) {
    public static ErrorDto of(String code, String message) {
        return new ErrorDto(code, message, Map.of());
    }
}
