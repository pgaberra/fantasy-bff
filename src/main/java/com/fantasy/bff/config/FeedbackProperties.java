package com.fantasy.bff.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Bug reports and feature requests from signed-in users, filed as issues in a private GitHub
 * repository. Off unless switched on; switched on without a token, the instance does not start.
 *
 * <p>The repository must be private: an issue carries the reporter's email address so the reply
 * can go out by mail, and the message itself is whatever the user typed.
 */
@ConfigurationProperties("feedback")
@Validated
public record FeedbackProperties(boolean enabled, @Valid @NotNull Github github) {

    public record Github(
            String token,
            @NotBlank @Pattern(regexp = "^[\\w.-]+/[\\w.-]+$", message = "must be owner/name") String repository,
            @NotBlank String apiBaseUrl,
            @Positive int timeoutMs
    ) {
        public String owner() {
            return repository.substring(0, repository.indexOf('/'));
        }

        public String name() {
            return repository.substring(repository.indexOf('/') + 1);
        }
    }

    @AssertTrue(message = "FEEDBACK_GITHUB_TOKEN must be set when FEEDBACK_ENABLED is true")
    public boolean isTokenSetWhenEnabled() {
        return !enabled || (github != null && StringUtils.hasText(github.token()));
    }
}
