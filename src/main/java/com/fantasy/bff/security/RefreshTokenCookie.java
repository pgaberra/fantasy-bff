package com.fantasy.bff.security;

import com.fantasy.bff.config.JwtProperties;
import com.fantasy.bff.config.RefreshCookieProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The refresh token as an HttpOnly cookie on the API host. A token the web keeps in localStorage
 * is wiped by Safari after seven days without a visit, which made the 30-day session a 7-day one;
 * a cookie the server sets is not under that cap.
 *
 * <p>Host-only (no {@code Domain}), scoped to {@code /api/v1/auth} so it travels to the auth
 * endpoints and nowhere else, and {@code SameSite=Lax} so no cross-site request carries it.
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "slapstat_refresh";
    public static final String PATH = "/api/v1/auth";
    public static final int MAX_VALUE_LENGTH = 4096;

    private final boolean secure;
    private final Duration maxAge;

    public RefreshTokenCookie(RefreshCookieProperties properties, JwtProperties jwtProperties) {
        this.secure = properties.secure();
        this.maxAge = Duration.ofMillis(jwtProperties.refreshExpirationMs());
    }

    public ResponseCookie issue(String refreshToken) {
        return base(refreshToken).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH);
    }
}
