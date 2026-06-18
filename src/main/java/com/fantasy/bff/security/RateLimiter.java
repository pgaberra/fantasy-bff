package com.fantasy.bff.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window rate limiter. The BFF runs a single instance per environment,
 * so per-process counters are sufficient. Stale windows are evicted lazily once the map
 * grows past a bound, so memory stays flat without a background thread.
 */
@Component
public class RateLimiter {

    private static final int MAX_TRACKED_KEYS = 100_000;

    private record Window(long resetAtEpochSec, int count) {}

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records a request against {@code key} and reports whether it is within the limit.
     *
     * @return true if the request is allowed, false once the limit for the window is exceeded
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.instant().getEpochSecond();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(entry -> now >= entry.getValue().resetAtEpochSec());
        }
        long windowSeconds = window.toSeconds();
        int[] count = new int[1];
        windows.compute(key, (ignored, existing) -> {
            if (existing == null || now >= existing.resetAtEpochSec()) {
                count[0] = 1;
                return new Window(now + windowSeconds, 1);
            }
            count[0] = existing.count() + 1;
            return new Window(existing.resetAtEpochSec(), existing.count() + 1);
        });
        return count[0] <= limit;
    }
}
