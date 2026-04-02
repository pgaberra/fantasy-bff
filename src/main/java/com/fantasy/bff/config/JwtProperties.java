package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@ConfigurationProperties("security.jwt")
@Validated
public record JwtProperties(
    @NotBlank String secret,
    @Positive long expirationMs,
    @Positive long refreshExpirationMs
) {}
