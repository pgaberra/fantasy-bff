package com.fantasy.bff.security;

import com.fantasy.bff.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public final class JwtTokenValidator {

    private final SecretKey signingKey;
    private final JwtProperties jwtProperties;

    public JwtTokenValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ADMIN = "admin";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    public String generateToken(String userId, String email) {
        return generateToken(userId, email, false);
    }

    public String generateToken(String userId, String email, boolean admin) {
        return buildToken(userId, email, TYPE_ACCESS, jwtProperties.expirationMs(), admin);
    }

    public String generateRefreshToken(String userId, String email) {
        return buildToken(userId, email, TYPE_REFRESH, jwtProperties.refreshExpirationMs(), false);
    }

    private String buildToken(String userId, String email, String type, long expirationMs, boolean admin) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        var builder = Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiry);
        if (admin) {
            builder.claim(CLAIM_ADMIN, true);
        }
        return builder.signWith(signingKey).compact();
    }

    public Claims validateAndExtractAccessTokenClaims(String token) {
        Claims claims = parseAndValidate(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new SecurityException("Expected access token");
        }
        return claims;
    }

    public Claims validateAndExtractRefreshTokenClaims(String token) {
        Claims claims = parseAndValidate(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new SecurityException("Expected refresh token");
        }
        return claims;
    }

    private Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new SecurityException("Invalid or expired JWT token", e);
        }
    }

    public long getExpirationMs() {
        return jwtProperties.expirationMs();
    }

    public long getRefreshExpirationMs() {
        return jwtProperties.refreshExpirationMs();
    }
}
