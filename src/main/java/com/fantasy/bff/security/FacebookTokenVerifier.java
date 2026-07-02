package com.fantasy.bff.security;

/**
 * Verifies a Facebook user access token (validity + that it was issued for our app) and
 * extracts the identity. Implementations must throw {@link SecurityException} for any
 * token that cannot be fully trusted.
 */
public interface FacebookTokenVerifier {

    FacebookIdentity verify(String accessToken);
}
