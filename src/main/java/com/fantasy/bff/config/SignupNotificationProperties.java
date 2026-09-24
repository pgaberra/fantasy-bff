package com.fantasy.bff.config;

import jakarta.validation.constraints.Email;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Where the owner is told of each new account. Optional: left blank, no one is told and the
 * sender says so once at startup, so a deployment that has not set the variable yet still starts.
 */
@ConfigurationProperties("signup-notification")
@Validated
public record SignupNotificationProperties(@Email String notifyEmail) {

    public boolean enabled() {
        return StringUtils.hasText(notifyEmail);
    }
}
