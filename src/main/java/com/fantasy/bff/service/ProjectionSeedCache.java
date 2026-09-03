package com.fantasy.bff.service;

import com.fantasy.bff.service.ProjectionSeedService.Seed;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * The model's seeded board, held between the readers who ask for it.
 *
 * <p>Building one is the most expensive read this service does: every active player and every
 * skater and goalie projection from the projection service, then a name-and-sweater resolution
 * of that whole board against the platform's pool. The limits a caller passes trim what is
 * sent and nothing else, so a five-row preview costs exactly what creating a projection costs
 * — and the create page asks for one every time the AI starting point is picked. Held here,
 * only the first of those pays.
 *
 * <p>The seed is the same for everyone: it is the model talking, not a user's own board, so
 * one entry serves every reader. That is also why the entries are keyed by the season and
 * model version they were built from and by nothing else.
 */
@Component
public class ProjectionSeedCache {

    /**
     * How long a seed is served again before it is rebuilt. The model's lines change only when
     * the nightly sync re-runs it, so the staleness this can cause is half an hour on one night
     * a day, against a rebuild on every click of a radio button.
     */
    static final Duration TTL = Duration.ofMinutes(30);

    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public ProjectionSeedCache() {
        this(Clock.systemUTC());
    }

    ProjectionSeedCache(Clock clock) {
        this.clock = clock;
    }

    Optional<Seed> get(int season, String modelVersion) {
        Key key = new Key(season, modelVersion);
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (Duration.between(entry.storedAt(), clock.instant()).compareTo(TTL) >= 0) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.seed());
    }

    /**
     * Two readers arriving on a cold entry together both build it and both store it, rather than
     * one holding a lock across the other's downstream calls. They build the same board, so the
     * only cost is the work itself, and only until the first of them lands.
     *
     * <p>Only one season and model version is ever in play, so the map cannot grow: a changed
     * version adds an entry that the old one's expiry then clears.
     */
    void put(int season, String modelVersion, Seed seed) {
        entries.put(new Key(season, modelVersion), new Entry(seed, clock.instant()));
    }

    /** Drops everything held, so that a test starts from empty. */
    public void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    private record Key(int season, String modelVersion) {
    }

    private record Entry(Seed seed, Instant storedAt) {
    }
}
