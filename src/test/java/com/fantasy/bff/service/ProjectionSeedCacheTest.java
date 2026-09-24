package com.fantasy.bff.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fantasy.bff.service.ProjectionSeedService.Seed;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectionSeedCacheTest {

    private final MovableClock clock = new MovableClock(Instant.parse("2026-09-03T12:00:00Z"));
    private final ProjectionSeedCache cache = new ProjectionSeedCache(clock);

    private static final Seed SEED = new Seed(List.of(), "marcel-v3", 400, 60, 3, Set.of(6001, 6002), 1);

    @Test
    void handsBackWhatWasPutIn() {
        cache.put(2026, "marcel-v3", SEED);

        assertThat(cache.get(2026, "marcel-v3")).contains(SEED);
    }

    @Test
    void hasNothingForASeasonItWasNeverGiven() {
        assertThat(cache.get(2026, "marcel-v3")).isEmpty();
    }

    /** A rerun of the model publishes under a new version, and must not read the old one's board. */
    @Test
    void keepsTheVersionsApart() {
        cache.put(2026, "marcel-v3", SEED);

        assertThat(cache.get(2026, "marcel-v4")).isEmpty();
        assertThat(cache.get(2025, "marcel-v3")).isEmpty();
    }

    /**
     * The nightly sync recomputes the model's lines under the same version, so nothing tells this
     * cache the board has changed. Expiry is what stops it serving yesterday's for the life of the
     * process.
     */
    @Test
    void forgetsASeedOnceItIsOldEnough() {
        cache.put(2026, "marcel-v3", SEED);

        clock.advance(ProjectionSeedCache.TTL.minus(Duration.ofMinutes(1)));
        assertThat(cache.get(2026, "marcel-v3")).isNotEmpty();

        clock.advance(Duration.ofMinutes(2));
        assertThat(cache.get(2026, "marcel-v3")).isEmpty();
    }

    /** An expired entry is dropped when it is asked for, rather than left to sit on the heap. */
    @Test
    void dropsAnExpiredEntryRatherThanKeepingIt() {
        cache.put(2026, "marcel-v3", SEED);
        clock.advance(ProjectionSeedCache.TTL.plus(Duration.ofMinutes(1)));

        cache.get(2026, "marcel-v3");

        assertThat(cache.size()).isZero();
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
