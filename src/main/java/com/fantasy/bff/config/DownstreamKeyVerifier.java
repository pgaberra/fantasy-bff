package com.fantasy.bff.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DownstreamKeyVerifier {

    private static final Logger log = LoggerFactory.getLogger(DownstreamKeyVerifier.class);
    private static final String SENTINEL = "startup-key-check";

    enum Result {
        OK,
        KEY_REJECTED,
        UNREACHABLE,
        SKIPPED
    }

    private final RestClient databaseServiceClient;
    private final RestClient yahooFantasyServiceClient;
    private final RestClient espnFantasyServiceClient;
    private final boolean dbKeyConfigured;
    private final boolean yahooKeyConfigured;
    private final boolean espnKeyConfigured;

    public DownstreamKeyVerifier(
            RestClient databaseServiceClient,
            RestClient yahooFantasyServiceClient,
            RestClient espnFantasyServiceClient,
            @Value("${services.database.api-key:}") String dbApiKey,
            @Value("${services.yahoo-fantasy.api-key:}") String yahooApiKey,
            @Value("${services.espn-fantasy.api-key:}") String espnApiKey) {
        this.databaseServiceClient = databaseServiceClient;
        this.yahooFantasyServiceClient = yahooFantasyServiceClient;
        this.espnFantasyServiceClient = espnFantasyServiceClient;
        this.dbKeyConfigured = StringUtils.hasText(dbApiKey);
        this.yahooKeyConfigured = StringUtils.hasText(yahooApiKey);
        this.espnKeyConfigured = StringUtils.hasText(espnApiKey);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyOnStartup() {
        Thread.ofVirtual().name("db-key-check").start(() -> verify(
                "fantasy-db-service", "DB_INTERNAL_API_KEY", dbKeyConfigured,
                databaseServiceClient, "/api/v1/users/exists", "email"));
        Thread.ofVirtual().name("yahoo-key-check").start(() -> verify(
                "fantasy-yahoo-service", "YAHOO_INTERNAL_API_KEY", yahooKeyConfigured,
                yahooFantasyServiceClient, "/api/v1/yahoo/oauth/connection", "appUserId"));
        Thread.ofVirtual().name("espn-key-check").start(() -> verify(
                "fantasy-espn-service", "ESPN_INTERNAL_API_KEY", espnKeyConfigured,
                espnFantasyServiceClient, "/api/v1/espn/credentials", "appUserId"));
    }

    Result verify(String service, String bffEnvVar, boolean keyConfigured,
                  RestClient client, String path, String param) {
        if (!keyConfigured) {
            log.debug("Skipping internal-key check for {} ({} is unset — local/dev)", service, bffEnvVar);
            return Result.SKIPPED;
        }
        try {
            client.get()
                    .uri(uriBuilder -> uriBuilder.path(path).queryParam(param, SENTINEL).build())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Internal API key accepted by {} — startup check OK", service);
            return Result.OK;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                log.error("Internal API key REJECTED by {} (401 at startup). The bff {} does not "
                        + "match {}'s INTERNAL_API_KEY — align them and redeploy.",
                        service, bffEnvVar, service);
                return Result.KEY_REJECTED;
            }
            log.warn("Unexpected {} from {} during the startup key check (key not rejected)",
                    e.getStatusCode(), service);
            return Result.UNREACHABLE;
        } catch (RestClientException e) {
            log.warn("Could not reach {} for the startup key check (down or still starting)", service, e);
            return Result.UNREACHABLE;
        }
    }
}
