package com.fantasy.bff.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bug reports and feature requests from signed-in users, filed as issues in a private GitHub
 * repository. Always served, so a missing or blank token stops startup.
 *
 * <p>The repository must be private: an issue carries the reporter's email address so the reply
 * can go out by mail, and the message itself is whatever the user typed. {@code notifyEmail} is told
 * each time one is filed, since GitHub does not notify the token's owner of an issue they opened.
 */
@ConfigurationProperties("feedback")
@Validated
public record FeedbackProperties(@Valid @NotNull Github github, @NotBlank @Email String notifyEmail) {

    public record Github(
            @NotBlank String token,
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
}
