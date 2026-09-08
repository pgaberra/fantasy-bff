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
 * Sends the email-verification email via Resend's REST API. When {@code RESEND_API_KEY} is
 * unset (local dev / CI / tests) the sender is inert and logs the verification link instead
 * of sending — mirroring {@link ResendPasswordResetEmailSender}. A send failure is logged at
 * ERROR (so it reaches Sentry) but never thrown, so registration and resend requests still
 * respond identically regardless of whether the email went out.
 */
@Component
public class ResendEmailVerificationEmailSender implements EmailVerificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailVerificationEmailSender.class);

    private final RestClient resendClient;
    private final String apiKey;
    private final String from;

    public ResendEmailVerificationEmailSender(
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
    public void send(String toEmail, String verifyLink, Instant expiresAt) {
        if (!StringUtils.hasText(apiKey)) {
            log.info("Email sending disabled (RESEND_API_KEY unset); verification link for {}: {}",
                    toEmail.replace("\r", "_").replace("\n", "_"), verifyLink);
            return;
        }
        long hours = Math.max(1, Duration.between(Instant.now(), expiresAt).toHours());
        Map<String, Object> payload = Map.of(
                "from", from,
                "to", List.of(toEmail),
                "subject", "Verify your SlapStat email",
                "text", textBody(verifyLink, hours),
                "html", htmlBody(verifyLink, hours));
        try {
            resendClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Failed to send email verification email via Resend", e);
        }
    }

    private String textBody(String verifyLink, long hours) {
        return "Welcome to SlapStat. Please confirm your email address.\n\n"
                + "Open this link to verify your email address (expires in about " + hours + " hours):\n"
                + verifyLink + "\n\n"
                + "If you didn't create a SlapStat account, you can ignore this email.";
    }

    private String htmlBody(String verifyLink, long hours) {
        return "<p>Welcome to SlapStat. Please confirm your email address.</p>"
                + "<p><a href=\"" + verifyLink + "\">Verify your email</a> "
                + "(expires in about " + hours + " hours).</p>"
                + "<p>If you didn't create a SlapStat account, you can ignore this email.</p>";
    }
}
