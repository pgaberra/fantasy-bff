package com.fantasy.bff.email;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Transactional email. Password reset and email verification both depend on it, so a deployed
 * instance without a Resend key does not start.
 *
 * <p>{@code logLinks} is for a local run only, and only the {@code dev} profile turns it on: with
 * no key, the senders then write the reset and verification links to the log so the flows can
 * be clicked through. Each link carries a live single-use token, which is why this is never the
 * fallback anywhere else.
 */
@ConfigurationProperties("email")
@Validated
public record EmailProperties(
        @NotBlank String from,
        @Valid @NotNull Resend resend,
        boolean logLinks
) {
    public record Resend(String apiKey, @NotBlank String baseUrl, @Positive int timeoutMs) {
    }

    public boolean sendingEnabled() {
        return resend != null && StringUtils.hasText(resend.apiKey());
    }

    @AssertTrue(message = "RESEND_API_KEY must be set; only a local run (the dev profile, which sets "
            + "email.log-links=true) may leave it blank")
    public boolean isResendKeySetOrLocalRun() {
        return sendingEnabled() || logLinks;
    }
}
