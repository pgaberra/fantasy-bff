package com.fantasy.bff.config;

import com.fantasy.bff.email.EmailProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the real application.yaml, so it proves both halves of the fail-fast rule: the variable
 * maps to the property, and a blank value stops startup rather than being sent downstream.
 */
class RequiredSecretsTest {

    @EnableConfigurationProperties({InternalApiKeyProperties.class, EmailProperties.class, FeedbackProperties.class})
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesOnly.class)
            .withPropertyValues(
                    "DB_INTERNAL_API_KEY=db-key",
                    "YAHOO_INTERNAL_API_KEY=yahoo-key",
                    "ESPN_INTERNAL_API_KEY=espn-key",
                    "PROJECTION_INTERNAL_API_KEY=projection-key",
                    "RESEND_API_KEY=re_key");

    @Test
    void bindsEveryInternalKeyFromItsVariable() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            InternalApiKeyProperties keys = context.getBean(InternalApiKeyProperties.class);
            assertThat(keys.database().apiKey()).isEqualTo("db-key");
            assertThat(keys.yahooFantasy().apiKey()).isEqualTo("yahoo-key");
            assertThat(keys.espnFantasy().apiKey()).isEqualTo("espn-key");
            assertThat(keys.projection().apiKey()).isEqualTo("projection-key");
            assertThat(context.getBean(EmailProperties.class).logLinks()).isFalse();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DB_INTERNAL_API_KEY", "YAHOO_INTERNAL_API_KEY", "ESPN_INTERNAL_API_KEY", "PROJECTION_INTERNAL_API_KEY"})
    void aBlankInternalKeyStopsStartup(String variable) {
        runner.withPropertyValues(variable + "=  ").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aBlankResendKeyStopsStartupOutsideALocalRun() {
        runner.withPropertyValues("RESEND_API_KEY=").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void feedbackIsOffAndNeedsNoTokenUnlessSwitchedOn() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            FeedbackProperties feedback = context.getBean(FeedbackProperties.class);
            assertThat(feedback.enabled()).isFalse();
            assertThat(feedback.github().repository()).isEqualTo("pgaberra/slapstat-feedback");
        });
    }

    @Test
    void feedbackSwitchedOnWithoutATokenStopsStartup() {
        runner.withPropertyValues("FEEDBACK_ENABLED=true", "FEEDBACK_GITHUB_TOKEN=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void feedbackSwitchedOnReadsItsTokenFromTheVariable() {
        runner.withPropertyValues("FEEDBACK_ENABLED=true", "FEEDBACK_GITHUB_TOKEN=github_pat_x").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FeedbackProperties.class).github().token()).isEqualTo("github_pat_x");
        });
    }

    @Test
    void theDevProfileRunsWithoutResendAndLogsLinksInstead() {
        runner.withPropertyValues("RESEND_API_KEY=", "spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            EmailProperties email = context.getBean(EmailProperties.class);
            assertThat(email.sendingEnabled()).isFalse();
            assertThat(email.logLinks()).isTrue();
        });
    }

    @Test
    void theStagingProfileDoesNotLogLinks() {
        runner.withPropertyValues("RESEND_API_KEY=", "spring.profiles.active=staging")
                .run(context -> assertThat(context).hasFailed());
    }
}
