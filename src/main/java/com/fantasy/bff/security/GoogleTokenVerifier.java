package com.fantasy.bff.security;

/**
 * Verifies a Google ID token (signature, issuer, audience, expiry, verified email)
 * and extracts the identity claims. Implementations must throw
 * {@link SecurityException} for any token that cannot be fully trusted.
 */
public interface GoogleTokenVerifier {

    GoogleIdentity verify(String idToken);
}
