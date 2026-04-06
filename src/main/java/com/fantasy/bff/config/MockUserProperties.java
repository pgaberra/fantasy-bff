package com.fantasy.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "mock.user")
@Profile("mock")
@Validated
public record MockUserProperties(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
