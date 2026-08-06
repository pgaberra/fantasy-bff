package com.fantasy.bff.payments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum WebhookEventType {
    SUBSCRIPTION_CREATED("subscription_created"),
    SUBSCRIPTION_UPDATED("subscription_updated"),
    SUBSCRIPTION_CANCELED("subscription_canceled"),
    SUBSCRIPTION_PAST_DUE("subscription_past_due");

    private final String code;

    WebhookEventType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static WebhookEventType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown webhook event type: " + code));
    }
}
