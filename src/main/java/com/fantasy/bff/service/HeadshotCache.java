package com.fantasy.bff.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The avatars this service has already framed, so a table of them is drawn once rather than once
 * per reader.
 *
 * <p>Drawing one is cheap on its own — a few hundred pixels, about seven milliseconds — but every
 * one of them first fetches the source from the platform's CDN, and a player table asks for fifty
 * at a time. Measured on staging with a cold browser cache, a page's worth came back at a median
 * of 844 ms each and a slowest of 1264 ms: not the drawing, but fifty concurrent fetches queueing
 * behind each other on a two-core box. Held here, the second reader of a player pays neither.
 *
 * <p>It fills on demand, so it holds the players people actually looked at rather than the pool.
 * Entries expire so that a long-lived process cannot keep serving a picture the platform has
 * replaced; the browser's own week of {@code Cache-Control} is the longer of the two caches
 * regardless, which is why a day is generous enough here.
 *
 * <p>A miss is not remembered. The pool that has no pictures at all today is the one whose sync
 * is expected to fill them in, and a remembered 404 would outlive that.
 */
@Component
public class HeadshotCache {

    /**
     * Long enough that a reader rarely pays twice, short enough that a replaced picture cannot be
     * held indefinitely. The browser is told to keep its copy for a week, so this is not what
     * decides how fast a new picture is seen.
     */
    static final Duration TTL = Duration.ofHours(24);

    /**
     * A little above the size of a player pool, so a full table fits and a runaway cannot. Passing
     * it empties the cache rather than evicting cleverly: the set is bounded by the pool, so going
     * over means something is asking for players that do not exist, and the next table refills it.
     */
    static final int MAX_ENTRIES = 2_000;

    private final Map<Integer, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public HeadshotCache() {
        this(Clock.systemUTC());
    }

    HeadshotCache(Clock clock) {
        this.clock = clock;
    }

    Optional<byte[]> get(int playerId) {
        Entry entry = entries.get(playerId);
        if (entry == null) {
            return Optional.empty();
        }
        if (Duration.between(entry.storedAt(), clock.instant()).compareTo(TTL) >= 0) {
            entries.remove(playerId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.image());
    }

    /**
     * Two readers arriving on a cold entry together both draw it and both store it, rather than
     * one holding a lock across the other's network call. They draw the same bytes, so the only
     * cost is the work itself, and only until the first of them lands.
     */
    void put(int playerId, byte[] image) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear();
        }
        entries.put(playerId, new Entry(image, clock.instant()));
    }

    int size() {
        return entries.size();
    }

    /** Drops everything held, so that a test starts from empty. */
    public void clear() {
        entries.clear();
    }

    private record Entry(byte[] image, Instant storedAt) {
    }
}
