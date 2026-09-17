package com.fantasy.bff.client;

import com.fantasy.bff.exception.YahooAccessDeniedException;
import com.fantasy.bff.generated.yahoo.model.AuthorizeUrlResponse;
import com.fantasy.bff.generated.yahoo.model.CompleteLinkRequest;
import com.fantasy.bff.generated.yahoo.model.ConnectionResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueDraftResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueTeamsResponse;
import com.fantasy.bff.generated.yahoo.model.LeaguesResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@link YahooServiceClient} that talks to fantasy-yahoo-service over HTTP.
 *
 * The yahoo-service serves data in an already-frontend-friendly shape, so the BFF
 * passes the generated models straight through. The app user id (the JWT subject) is
 * forwarded as the {@code appUserId} query param; the service keys each user's Yahoo
 * tokens by it.
 *
 * Uses model classes generated from specs/fantasy-yahoo-service-openapi.yaml —
 * if the yahoo-service API changes, update the spec and re-run ./gradlew generateYahooClient.
 */
@Component
public class HttpYahooServiceClient implements YahooServiceClient {

    private static final String REFUSED = "Yahoo refused the request";
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;

    public HttpYahooServiceClient(@Qualifier("yahooFantasyServiceClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AuthorizeUrlResponse authorizeUrl(String appUserId) {
        return restClient.post()
                .uri(b -> b.path("/api/v1/yahoo/oauth/authorize-url").queryParam("appUserId", appUserId).build())
                .retrieve()
                .body(AuthorizeUrlResponse.class);
    }

    @Override
    public ConnectionResponse connection(String appUserId) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/yahoo/oauth/connection").queryParam("appUserId", appUserId).build())
                .retrieve()
                .body(ConnectionResponse.class);
    }

    @Override
    public ConnectionResponse completeLink(String appUserId, String code) {
        return restClient.post()
                .uri("/api/v1/yahoo/oauth/complete")
                .body(new CompleteLinkRequest().appUserId(appUserId).code(code))
                .retrieve()
                .body(ConnectionResponse.class);
    }

    @Override
    public LeaguesResponse leagues(String appUserId) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/yahoo/leagues").queryParam("appUserId", appUserId).build())
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.FORBIDDEN.value(),
                        HttpYahooServiceClient::throwRefusal)
                .body(LeaguesResponse.class);
    }

    @Override
    public LeagueSettingsResponse settings(String appUserId, String leagueKey) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/yahoo/leagues/{leagueKey}/settings")
                        .queryParam("appUserId", appUserId).build(leagueKey))
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.FORBIDDEN.value(),
                        HttpYahooServiceClient::throwRefusal)
                .body(LeagueSettingsResponse.class);
    }

    @Override
    public LeagueTeamsResponse teams(String appUserId, String leagueKey) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/yahoo/leagues/{leagueKey}/teams")
                        .queryParam("appUserId", appUserId).build(leagueKey))
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.FORBIDDEN.value(),
                        HttpYahooServiceClient::throwRefusal)
                .body(LeagueTeamsResponse.class);
    }

    @Override
    public LeagueDraftResponse draft(String appUserId, String leagueKey) {
        return restClient.get()
                .uri(b -> b.path("/api/v1/yahoo/leagues/{leagueKey}/draft")
                        .queryParam("appUserId", appUserId).build(leagueKey))
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.FORBIDDEN.value(),
                        HttpYahooServiceClient::throwRefusal)
                .body(LeagueDraftResponse.class);
    }

    /**
     * yahoo-service answers 403 only when Yahoo refused, and puts Yahoo's sentence in the error's
     * {@code message}. Its internal API key failing is a 401, so a 403 is never ours.
     */
    private static void throwRefusal(org.springframework.http.HttpRequest request, ClientHttpResponse response)
            throws IOException {
        throw new YahooAccessDeniedException(refusalMessage(
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    static String refusalMessage(String body) {
        try {
            JsonNode message = JSON.readTree(body).path("message");
            if (message.isString() && !message.asString().isBlank()) {
                String oneLine = message.asString().replace("\r", " ").replace("\n", " ");
                return oneLine.length() > MAX_MESSAGE_LENGTH ? oneLine.substring(0, MAX_MESSAGE_LENGTH) : oneLine;
            }
        } catch (RuntimeException e) {
            // Not yahoo-service's ErrorDto: the status alone still says Yahoo refused.
        }
        return REFUSED;
    }
}
