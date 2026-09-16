package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.ServiceVersion;
import com.fantasy.bff.dto.response.VersionsResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reports each service's deployed version. The endpoint is public, so an answer is kept for a few
 * seconds and shared by everyone who asks in that time: without it every request fans out to four
 * services. Short enough that a promotion check polling for a new version still sees it land.
 */
@Service
public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);

    static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final String ownVersion;
    private final RestClient databaseServiceClient;
    private final RestClient yahooFantasyServiceClient;
    private final RestClient espnFantasyServiceClient;
    private final RestClient projectionServiceClient;
    private final PlayerPoolSource playerPool;
    private final Clock clock;
    private final Duration cacheTtl;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile CachedVersions cached;

    @Autowired
    public VersionService(
            @Value("${info.app.version:dev}") String ownVersion,
            RestClient databaseServiceClient,
            RestClient yahooFantasyServiceClient,
            RestClient espnFantasyServiceClient,
            RestClient projectionServiceClient,
            PlayerPoolSource playerPool) {
        this(ownVersion, databaseServiceClient, yahooFantasyServiceClient, espnFantasyServiceClient,
                projectionServiceClient, playerPool, Clock.systemUTC(), CACHE_TTL);
    }

    VersionService(
            String ownVersion,
            RestClient databaseServiceClient,
            RestClient yahooFantasyServiceClient,
            RestClient espnFantasyServiceClient,
            RestClient projectionServiceClient,
            PlayerPoolSource playerPool,
            Clock clock,
            Duration cacheTtl) {
        this.ownVersion = ownVersion;
        this.databaseServiceClient = databaseServiceClient;
        this.yahooFantasyServiceClient = yahooFantasyServiceClient;
        this.espnFantasyServiceClient = espnFantasyServiceClient;
        this.projectionServiceClient = projectionServiceClient;
        this.playerPool = playerPool;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
    }

    public VersionsResponse getVersions() {
        CachedVersions current = cached;
        if (current != null && current.isFreshAt(clock.instant())) {
            return current.response();
        }
        refreshLock.lock();
        try {
            current = cached;
            if (current != null && current.isFreshAt(clock.instant())) {
                return current.response();
            }
            VersionsResponse response = probeAll();
            cached = new CachedVersions(response, clock.instant().plus(cacheTtl));
            return response;
        } finally {
            refreshLock.unlock();
        }
    }

    private VersionsResponse probeAll() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ServiceVersion> db = executor.submit(probe("fantasy-db-service", databaseServiceClient));
            Future<ServiceVersion> yahoo = executor.submit(probe("fantasy-yahoo-service", yahooFantasyServiceClient));
            Future<ServiceVersion> espn = executor.submit(probe("fantasy-espn-service", espnFantasyServiceClient));
            // FastAPI, but it serves /actuator/info in Actuator's shape for exactly this probe.
            Future<ServiceVersion> projection =
                    executor.submit(probe("fantasy-projection-service", projectionServiceClient));
            return new VersionsResponse(
                    List.of(
                            new ServiceVersion("fantasy-bff", true, ownVersion),
                            awaitResult(db),
                            awaitResult(yahoo),
                            awaitResult(espn),
                            awaitResult(projection)),
                    playerPool.platform());
        }
    }

    private Callable<ServiceVersion> probe(String name, RestClient client) {
        return () -> {
            try {
                ActuatorInfo info = client.get().uri("/actuator/info").retrieve().body(ActuatorInfo.class);
                String version = info != null && info.app() != null ? info.app().version() : null;
                return new ServiceVersion(name, true, version);
            } catch (Exception exception) {
                log.warn("Could not read version from {}: {}", name,
                        exception.toString().replace("\r", "_").replace("\n", "_"));
                return new ServiceVersion(name, false, null);
            }
        };
    }

    private static ServiceVersion awaitResult(Future<ServiceVersion> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while probing service versions", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to probe service version", exception);
        }
    }

    private record CachedVersions(VersionsResponse response, Instant expiresAt) {
        boolean isFreshAt(Instant now) {
            return now.isBefore(expiresAt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ActuatorInfo(App app) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record App(String version) {}
    }
}
