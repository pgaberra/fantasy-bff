package com.fantasy.bff;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "security.jwt.secret=test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
        "services.database.api-key=test-db-key",
        "services.yahoo-fantasy.api-key=test-yahoo-key",
        "services.espn-fantasy.api-key=test-espn-key",
        "services.projection.api-key=test-projection-key",
        "email.resend.api-key=",
        "email.log-links=true",
        "security.rate-limit.enabled=false"
})
public abstract class BaseIntegrationTest {
}
