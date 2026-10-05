package com.fantasy.bff.security;

import com.fantasy.bff.config.JwtProperties;
import com.fantasy.bff.config.RefreshCookieProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieTest {

    private static final JwtProperties JWT =
            new JwtProperties("test-secret-key-that-is-long-enough-for-hmac-sha256", 900_000, 2_592_000_000L);

    @Test
    void issue_carriesTheTokenForTheRefreshLifetime_onTheAuthPathOnly() {
        ResponseCookie cookie = new RefreshTokenCookie(new RefreshCookieProperties(true), JWT).issue("the-token");

        assertThat(cookie.getName()).isEqualTo("slapstat_refresh");
        assertThat(cookie.getValue()).isEqualTo("the-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void clear_expiresTheSameCookieAtOnce() {
        ResponseCookie cookie = new RefreshTokenCookie(new RefreshCookieProperties(true), JWT).clear();

        assertThat(cookie.getName()).isEqualTo("slapstat_refresh");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void secure_followsTheProperty() {
        RefreshTokenCookie insecure = new RefreshTokenCookie(new RefreshCookieProperties(false), JWT);

        assertThat(insecure.issue("t").isSecure()).isFalse();
        assertThat(insecure.clear().isSecure()).isFalse();
    }

    @Test
    void secure_isOnWhenThePropertyIsUnset() {
        assertThat(new RefreshTokenCookie(new RefreshCookieProperties(null), JWT).issue("t").isSecure()).isTrue();
    }
}
