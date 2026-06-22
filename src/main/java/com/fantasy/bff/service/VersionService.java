package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.ServiceVersion;
import com.fantasy.bff.dto.response.VersionsResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);

    private final String ownVersion;
    private final RestClient databaseServiceClient;
    private final RestClient yahooFantasyServiceClient;

    public VersionService(
            @Value("${info.app.version:dev}") String ownVersion,
            RestClient databaseServiceClient,
            RestClient yahooFantasyServiceClient) {
        this.ownVersion = ownVersion;
        this.databaseServiceClient = databaseServiceClient;
        this.yahooFantasyServiceClient = yahooFantasyServiceClient;
    }

    public VersionsResponse getVersions() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ServiceVersion> db = executor.submit(probe("fantasy-db-service", databaseServiceClient));
            Future<ServiceVersion> yahoo = executor.submit(probe("fantasy-yahoo-service", yahooFantasyServiceClient));
            return new VersionsResponse(List.of(
                    new ServiceVersion("fantasy-bff", true, ownVersion),
                    awaitResult(db),
                    awaitResult(yahoo)));
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ActuatorInfo(App app) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record App(String version) {}
    }
}
