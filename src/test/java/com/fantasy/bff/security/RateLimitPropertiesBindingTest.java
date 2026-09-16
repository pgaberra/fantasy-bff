package com.fantasy.bff.security;

import com.fantasy.bff.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds the rules exactly as application.yaml declares them. A YAML map key keeps its slashes only
 * inside brackets ("[/api/v1/auth/login]"); written bare, the binder strips them to
 * "apiv1authlogin", no request path ever matches, and every limit silently does nothing, which is
 * how the deployed BFF ran until this test. The other rate-limit tests set their rules in bracket
 * form themselves, so they could not see it.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "security.jwt.secret=test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
        "services.database.api-key=test-db-key",
        "services.yahoo-fantasy.api-key=test-yahoo-key",
        "services.espn-fantasy.api-key=test-espn-key",
        "services.projection.api-key=test-projection-key",
        "email.resend.api-key=",
        "email.log-links=true"
})
class RateLimitPropertiesBindingTest {

    @Autowired
    private RateLimitProperties properties;

    @Test
    void everyConfiguredRuleKeepsTheRequestPathItGuards() {
        assertThat(properties.endpoints()).isNotEmpty();
        assertThat(properties.endpoints().keySet()).allSatisfy(key -> assertThat(key).startsWith("/api/v1/"));
        assertThat(properties.endpoints()).containsKeys(
                "/api/v1/auth/login", "/api/v1/auth/verify/resend", "/api/v1/shared/*/preview", "/api/v1/versions");
    }
}
