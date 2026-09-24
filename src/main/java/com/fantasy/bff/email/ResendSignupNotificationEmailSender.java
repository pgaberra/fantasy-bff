package com.fantasy.bff.email;

import com.fantasy.bff.config.SignupNotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Mails the owner each time an account is created, with the new user's address and how they signed
 * up. The mail goes out on its own virtual thread, so a sign-up never waits on Resend, and a failure
 * is logged at ERROR and never thrown: the account exists whatever happens to this mail. The user's
 * address goes into the mail only, never into a log line.
 */
@Component
public class ResendSignupNotificationEmailSender implements SignupNotificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendSignupNotificationEmailSender.class);

    private final RestClient resendClient;
    private final EmailProperties email;
    private final SignupNotificationProperties signup;
    private final Executor executor;

    @Autowired
    public ResendSignupNotificationEmailSender(EmailProperties email, SignupNotificationProperties signup) {
        this(email, signup, task -> Thread.ofVirtual().name("signup-notification").start(task));
    }

    ResendSignupNotificationEmailSender(EmailProperties email, SignupNotificationProperties signup,
                                        Executor executor) {
        this.email = email;
        this.signup = signup;
        this.executor = executor;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(email.resend().timeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(email.resend().timeoutMs()));
        this.resendClient = RestClient.builder()
                .baseUrl(email.resend().baseUrl())
                .requestFactory(factory)
                .build();
        if (!signup.enabled()) {
            log.warn("Sign-up notifications are off (SIGNUP_NOTIFY_EMAIL unset); no one is told of new accounts");
        }
    }

    @Override
    public void send(String userEmail, SignupMethod method) {
        if (!signup.enabled()) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    deliver(userEmail, method);
                } catch (RuntimeException e) {
                    log.error("Failed to send the sign-up notification", e);
                }
            });
        } catch (RuntimeException e) {
            log.error("Could not start sending the sign-up notification", e);
        }
    }

    private void deliver(String userEmail, SignupMethod method) {
        if (!email.sendingEnabled()) {
            if (email.logLinks()) {
                log.info("Email sending disabled (RESEND_API_KEY unset, local run); a sign-up notification was not sent");
            } else {
                log.error("Email sending is not configured (RESEND_API_KEY unset); a sign-up notification was not sent");
            }
            return;
        }
        Map<String, Object> payload = Map.of(
                "from", email.from(),
                "to", List.of(signup.notifyEmail()),
                "subject", "New SlapStat user (" + method.label() + ")",
                "text", "A new account was created on SlapStat.\n\nEmail: " + userEmail
                        + "\nSign-up method: " + method.label() + "\n");
        try {
            resendClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + email.resend().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Failed to send the sign-up notification via Resend", e);
        }
    }
}
