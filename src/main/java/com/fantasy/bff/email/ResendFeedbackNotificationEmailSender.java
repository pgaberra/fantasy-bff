package com.fantasy.bff.email;

import com.fantasy.bff.config.FeedbackProperties;
import com.fantasy.bff.dto.request.FeedbackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Tells us a feedback issue was filed. GitHub never notifies an account of its own actions, and the
 * issues are opened with the owner's own token, so without this mail a report would sit in the
 * repository unseen.
 *
 * <p>It carries the title and the link, not the message or the reporter's address: those stay in
 * the private repository. A failure is logged at ERROR and never thrown, since the issue already
 * exists and the user's report went through.
 */
@Component
public class ResendFeedbackNotificationEmailSender implements FeedbackNotificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendFeedbackNotificationEmailSender.class);

    private final RestClient resendClient;
    private final EmailProperties email;
    private final FeedbackProperties feedback;

    public ResendFeedbackNotificationEmailSender(EmailProperties email, FeedbackProperties feedback) {
        this.email = email;
        this.feedback = feedback;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(email.resend().timeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(email.resend().timeoutMs()));
        this.resendClient = RestClient.builder()
                .baseUrl(email.resend().baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public void send(FeedbackType type, String title, String issueUrl) {
        String kind = type == FeedbackType.BUG ? "bug report" : "feature request";
        if (!email.sendingEnabled()) {
            if (email.logLinks()) {
                log.info("Email sending disabled (RESEND_API_KEY unset, local run); a feedback notification was not sent");
            } else {
                log.error("Email sending is not configured (RESEND_API_KEY unset); a feedback notification was not sent");
            }
            return;
        }
        Map<String, Object> payload = Map.of(
                "from", email.from(),
                "to", List.of(feedback.notifyEmail()),
                "subject", "New " + kind + ": " + title,
                "text", "A " + kind + " was sent from SlapStat.\n\n" + title + "\n\n" + issueUrl + "\n");
        try {
            resendClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + email.resend().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Failed to send the feedback notification via Resend", e);
        }
    }
}
