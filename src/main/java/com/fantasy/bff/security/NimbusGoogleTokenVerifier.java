package com.fantasy.bff.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * {@link GoogleTokenVerifier} backed by Nimbus, validating against Google's published
 * JWKS. The decoder is built lazily on first use so the app boots (and tests run)
 * without network access; the JWKS itself is fetched and cached by Nimbus.
 */
@Component
public class NimbusGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    // Google issues ID tokens with either form of the issuer claim.
    private static final Set<String> GOOGLE_ISSUERS =
            Set.of("https://accounts.google.com", "accounts.google.com");

    private final String clientId;
    private volatile JwtDecoder decoder;

    public NimbusGoogleTokenVerifier(@Value("${security.google.client-id:}") String clientId) {
        this.clientId = clientId;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Google login is not configured (GOOGLE_CLIENT_ID is unset)");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            throw new SecurityException("Invalid Google ID token", e);
        }
        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            throw new SecurityException("Google account email is not verified");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new SecurityException("Google ID token carries no email");
        }
        return new GoogleIdentity(jwt.getSubject(), email);
    }

    private JwtDecoder decoder() {
        JwtDecoder result = decoder;
        if (result == null) {
            synchronized (this) {
                if (decoder == null) {
                    decoder = buildDecoder();
                }
                result = decoder;
            }
        }
        return result;
    }

    private JwtDecoder buildDecoder() {
        NimbusJwtDecoder built = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
        OAuth2TokenValidator<Jwt> issuer = new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                iss -> iss != null && GOOGLE_ISSUERS.contains(iss.toString()));
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(clientId));
        built.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), issuer, audience));
        return built;
    }
}
