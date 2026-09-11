package com.fantasy.bff.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Asks every downstream, once the app is up, whether it accepts our internal API key.
 *
 * <p>A 401 takes this instance out of service: readiness moves to {@code REFUSING_TRAFFIC}, so
 * {@code /actuator/health} answers 503, the container's health check fails, and a deploy that
 * brought a mismatched key rolls back rather than serving every call to that service as a 502.
 * A service that is down, or answers anything but 401, proves nothing about the key; that only
 * logs a warning, so a downstream restarting at the same moment cannot keep the bff down.
 */
@Component
public class DownstreamKeyVerifier {

    private static final Logger log = LoggerFactory.getLogger(DownstreamKeyVerifier.class);
    private static final String SENTINEL = "startup-key-check";

    enum Result {
        OK,
        KEY_REJECTED,
        UNREACHABLE
    }

    record Check(String service, String bffEnvVar, RestClient client, String uri) {
    }

    private final List<Check> checks;
    private final ApplicationEventPublisher events;

    public DownstreamKeyVerifier(
            RestClient databaseServiceClient,
            RestClient yahooFantasyServiceClient,
            RestClient espnFantasyServiceClient,
            RestClient projectionServiceClient,
            ApplicationEventPublisher events) {
        this.checks = List.of(
                new Check("fantasy-db-service", "DB_INTERNAL_API_KEY", databaseServiceClient,
                        "/api/v1/users/exists?email=" + SENTINEL),
                new Check("fantasy-yahoo-service", "YAHOO_INTERNAL_API_KEY", yahooFantasyServiceClient,
                        "/api/v1/yahoo/oauth/connection?appUserId=" + SENTINEL),
                new Check("fantasy-espn-service", "ESPN_INTERNAL_API_KEY", espnFantasyServiceClient,
                        "/api/v1/espn/credentials?appUserId=" + SENTINEL),
                new Check("fantasy-projection-service", "PROJECTION_INTERNAL_API_KEY", projectionServiceClient,
                        "/api/v1/splits/seasons"));
        this.events = events;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyOnStartup() {
        for (Check check : checks) {
            Thread.ofVirtual().name(check.service() + "-key-check").start(() -> verify(check));
        }
    }

    List<Check> checks() {
        return checks;
    }

    Result verify(Check check) {
        try {
            check.client().get()
                    .uri(check.uri())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Internal API key accepted by {} — startup check OK", check.service());
            return Result.OK;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                log.error("Internal API key REJECTED by {} (401 at startup). The bff {} does not "
                                + "match {}'s INTERNAL_API_KEY — align them and redeploy. Readiness is "
                                + "now REFUSING_TRAFFIC, so this instance reports itself unhealthy.",
                        check.service(), check.bffEnvVar(), check.service());
                AvailabilityChangeEvent.publish(events, this, ReadinessState.REFUSING_TRAFFIC);
                return Result.KEY_REJECTED;
            }
            log.warn("Unexpected {} from {} during the startup key check (key not rejected)",
                    e.getStatusCode(), check.service());
            return Result.UNREACHABLE;
        } catch (RestClientException e) {
            log.warn("Could not reach {} for the startup key check (down or still starting)",
                    check.service(), e);
            return Result.UNREACHABLE;
        }
    }
}
