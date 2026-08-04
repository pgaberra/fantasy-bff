package com.fantasy.bff.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exchanges a Google OAuth 2.0 authorization code for an ID token (the code flow), using the
 * confidential client credentials server-side. This backs the top-level-redirect Sign-In the
 * web uses so Google login works on browsers that block the embedded GSI button (notably iOS
 * Safari under Intelligent Tracking Prevention). No network happens at construction, so the
 * app boots and tests run without Google configured.
 *
 * <p>The redirect URI is validated against a configured allowlist before the exchange — Google
 * also enforces that it matches a registered URI, but we never trust an inbound value blindly.
 */
@Component
public class GoogleCodeExchanger {

    private static final Logger log = LoggerFactory.getLogger(GoogleCodeExchanger.class);

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final String clientId;
    private final String clientSecret;
    private final Set<String> allowedRedirectUris;
    private final RestClient restClient;

    public GoogleCodeExchanger(
            @Value("${security.google.client-id:}") String clientId,
            @Value("${security.google.client-secret:}") String clientSecret,
            @Value("${security.google.token-uri:https://oauth2.googleapis.com/token}") String tokenUri,
            @Value("${security.google.allowed-redirect-uris:}") String allowedRedirectUris) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.allowedRedirectUris = parseAllowedRedirectUris(allowedRedirectUris);
        this.restClient = RestClient.create(tokenUri);
    }

    /**
     * Exchanges {@code code} (obtained via {@code redirectUri}) for the Google ID token.
     *
     * @throws IllegalStateException    when Google login is not configured
     * @throws IllegalArgumentException when {@code redirectUri} is not on the allowlist (400)
     * @throws SecurityException        when Google rejects the code or the exchange fails (401)
     */
    public String exchange(String code, String redirectUri) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException(
                    "Google login is not configured (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET are unset)");
        }
        if (!allowedRedirectUris.contains(redirectUri)) {
            throw new IllegalArgumentException("Unrecognized redirect URI");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        String body;
        try {
            body = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                // Google itself faulted — a genuine fault: log at error so it reaches Sentry.
                log.error("Google token endpoint returned a server error: status={} body={}",
                        e.getStatusCode(), sanitize(e.getResponseBodyAsString()), e);
            } else {
                // A 4xx means an invalid/expired/reused code or a redirect_uri mismatch — an
                // expected client outcome, so log at warn (no stack trace / Sentry noise).
                log.warn("Google token exchange rejected: status={} body={}",
                        e.getStatusCode(), sanitize(e.getResponseBodyAsString()));
            }
            throw new SecurityException("Could not exchange the Google authorization code", e);
        } catch (RestClientException e) {
            // Google unreachable / timeout — a genuine fault: log at error so it reaches Sentry.
            log.error("Google token endpoint unreachable", e);
            throw new SecurityException("Could not exchange the Google authorization code", e);
        }

        try {
            TokenResponse token = body == null ? null : JSON.readValue(body, TokenResponse.class);
            if (token == null || token.idToken() == null || token.idToken().isBlank()) {
                log.error("Google token exchange succeeded but returned no id_token");
                throw new SecurityException("Google token exchange returned no ID token");
            }
            return token.idToken();
        } catch (JacksonException e) {
            log.error("Google token response was unparsable", e);
            throw new SecurityException("Could not exchange the Google authorization code", e);
        }
    }

    private static Set<String> parseAllowedRedirectUris(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(uri -> !uri.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String sanitize(String value) {
        return value == null ? null : value.replace("\r", "_").replace("\n", "_");
    }

    record TokenResponse(@JsonProperty("id_token") String idToken) {}
}
