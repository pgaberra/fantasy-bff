package com.fantasy.bff.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sends the password reset email via Resend's REST API. When {@code RESEND_API_KEY} is
 * unset (local dev / CI / tests) the sender is inert and logs the reset link instead of
 * sending — mirroring the Sentry "inert when unconfigured" pattern. A send failure is
 * logged at ERROR (so it reaches Sentry) but never thrown: the caller still responds
 * identically so a reset request never reveals whether an account exists.
 */
@Component
public class ResendPasswordResetEmailSender implements PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendPasswordResetEmailSender.class);

    private final RestClient resendClient;
    private final String apiKey;
    private final String from;

    public ResendPasswordResetEmailSender(
            @Value("${email.resend.api-key:}") String apiKey,
            @Value("${email.from:SlapStat <no-reply@slapstat.com>}") String from,
            @Value("${email.resend.base-url:https://api.resend.com}") String baseUrl,
            @Value("${email.resend.timeout-ms:5000}") int timeoutMs) {
        this.apiKey = apiKey;
        this.from = from;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.resendClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public void send(String toEmail, String resetLink, Instant expiresAt) {
        if (!StringUtils.hasText(apiKey)) {
            log.info("Email sending disabled (RESEND_API_KEY unset); password reset link for {}: {}",
                    toEmail.replace("\r", "_").replace("\n", "_"), resetLink);
            return;
        }
        long minutes = Math.max(1, Duration.between(Instant.now(), expiresAt).toMinutes());
        Map<String, Object> payload = Map.of(
                "from", from,
                "to", List.of(toEmail),
                "subject", "Reset your SlapStat password",
                "text", textBody(resetLink, minutes),
                "html", htmlBody(resetLink, minutes));
        try {
            resendClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Failed to send password reset email via Resend", e);
        }
    }

    private String textBody(String resetLink, long minutes) {
        return "We received a request to reset your SlapStat password.\n\n"
                + "Open this link to choose a new password (expires in about " + minutes + " minutes):\n"
                + resetLink + "\n\n"
                + "If you didn't request this, you can safely ignore this email.";
    }

    private String htmlBody(String resetLink, long minutes) {
        return "<p>We received a request to reset your SlapStat password.</p>"
                + "<p><a href=\"" + resetLink + "\">Choose a new password</a> "
                + "(expires in about " + minutes + " minutes).</p>"
                + "<p>If you didn't request this, you can safely ignore this email.</p>";
    }
}
