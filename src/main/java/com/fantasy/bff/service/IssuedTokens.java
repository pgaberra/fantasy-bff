package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.AuthResponse;

/**
 * A signed-in session as {@link AuthService} issues it: the body the client reads, and the refresh
 * token, which travels only in the HttpOnly cookie the controller sets — never in the body, where
 * a script on the page could read it.
 */
public record IssuedTokens(AuthResponse response, String refreshToken) {
}
