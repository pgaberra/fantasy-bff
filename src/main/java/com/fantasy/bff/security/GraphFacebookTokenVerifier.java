package com.fantasy.bff.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link FacebookTokenVerifier} backed by the Facebook Graph API: it validates the user
 * access token via {@code /debug_token} (checking validity and that the token was issued
 * for our app) using the app access token ({@code app-id|app-secret}), then reads the id
 * and email via {@code /me}. No network happens at construction, so the app boots and
 * tests run without Facebook configured.
 */
@Component
public class GraphFacebookTokenVerifier implements FacebookTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GraphFacebookTokenVerifier.class);

    private final String appId;
    private final String appSecret;
    private final RestClient restClient;

    public GraphFacebookTokenVerifier(
            @Value("${security.facebook.app-id:}") String appId,
            @Value("${security.facebook.app-secret:}") String appSecret,
            @Value("${security.facebook.graph-base-url:https://graph.facebook.com}") String graphBaseUrl) {
        this.appId = appId.trim();
        this.appSecret = appSecret.trim();
        this.restClient = RestClient.create(graphBaseUrl);
    }

    @Override
    public FacebookIdentity verify(String accessToken) {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new IllegalStateException(
                    "Facebook login is not configured (FACEBOOK_APP_ID / FACEBOOK_APP_SECRET are unset)");
        }
        DebugTokenResponse debug = debugToken(accessToken);
        DebugTokenResponse.Data data = debug == null ? null : debug.data();
        if (data == null || !data.isValid() || !appId.equals(data.appId())) {
            log.warn("Facebook token rejected: isValid={}, token app id={}, configured app id={}",
                    data != null && data.isValid(), data == null ? null : data.appId(), appId);
            throw new SecurityException("Invalid Facebook access token");
        }
        GraphUser user = fetchProfile(accessToken);
        if (user == null || user.email() == null || user.email().isBlank()) {
            log.warn("Facebook profile has no accessible email for token user id {}", data.userId());
            throw new SecurityException("Facebook account has no accessible email");
        }
        return new FacebookIdentity(user.id(), user.email());
    }

    private DebugTokenResponse debugToken(String accessToken) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/debug_token")
                            .queryParam("input_token", accessToken)
                            .queryParam("access_token", appId + "|" + appSecret)
                            .build())
                    .retrieve()
                    .body(DebugTokenResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Facebook /debug_token failed: status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new SecurityException("Could not verify the Facebook access token", e);
        } catch (RestClientException e) {
            log.error("Facebook /debug_token unreachable", e);
            throw new SecurityException("Could not verify the Facebook access token", e);
        }
    }

    private GraphUser fetchProfile(String accessToken) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/me")
                            .queryParam("fields", "id,email")
                            .queryParam("access_token", accessToken)
                            .build())
                    .retrieve()
                    .body(GraphUser.class);
        } catch (RestClientResponseException e) {
            log.error("Facebook /me failed: status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new SecurityException("Could not read the Facebook profile", e);
        } catch (RestClientException e) {
            log.error("Facebook /me unreachable", e);
            throw new SecurityException("Could not read the Facebook profile", e);
        }
    }

    record DebugTokenResponse(Data data) {
        record Data(
                @JsonProperty("app_id") String appId,
                @JsonProperty("is_valid") boolean isValid,
                @JsonProperty("user_id") String userId) {}
    }

    record GraphUser(String id, String email) {}
}
