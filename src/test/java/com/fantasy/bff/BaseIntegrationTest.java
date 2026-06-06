package com.fantasy.bff;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "security.jwt.secret=test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm"
})
public abstract class BaseIntegrationTest {
}
