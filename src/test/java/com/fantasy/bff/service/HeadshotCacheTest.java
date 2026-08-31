package com.fantasy.bff.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class HeadshotCacheTest {

    private final MovableClock clock = new MovableClock(Instant.parse("2026-08-31T12:00:00Z"));
    private final HeadshotCache cache = new HeadshotCache(clock);

    @Test
    void handsBackWhatWasPutIn() {
        byte[] image = {1, 2, 3};

        cache.put(7, image);

        assertThat(cache.get(7)).contains(image);
    }

    @Test
    void hasNothingForAPlayerItWasNeverGiven() {
        assertThat(cache.get(7)).isEmpty();
    }

    /**
     * A picture the platform has replaced must not be served for the life of the process. The
     * browser holds its own copy for a week, so a day here is not what decides how fast a reader
     * sees a new one — it is only what stops this one holding the old one forever.
     */
    @Test
    void forgetsAPictureOnceItIsOldEnough() {
        cache.put(7, new byte[] {1});

        clock.advance(HeadshotCache.TTL.minus(Duration.ofMinutes(1)));
        assertThat(cache.get(7)).isNotEmpty();

        clock.advance(Duration.ofMinutes(2));
        assertThat(cache.get(7)).isEmpty();
    }

    /** An expired entry is dropped when it is asked for, rather than left to sit on the heap. */
    @Test
    void dropsAnExpiredEntryRatherThanKeepingIt() {
        cache.put(7, new byte[] {1});
        clock.advance(HeadshotCache.TTL.plus(Duration.ofMinutes(1)));

        cache.get(7);

        assertThat(cache.size()).isZero();
    }

    /**
     * The keys are player ids from a pool of a couple of thousand, so going past the cap means
     * something is asking for players that do not exist. Emptying it bounds the heap without
     * pretending to know which of them mattered; the next table fills it again.
     */
    @Test
    void emptiesItselfRatherThanGrowingWithoutBound() {
        for (int id = 0; id < HeadshotCache.MAX_ENTRIES; id++) {
            cache.put(id, new byte[] {1});
        }
        assertThat(cache.size()).isEqualTo(HeadshotCache.MAX_ENTRIES);

        cache.put(HeadshotCache.MAX_ENTRIES, new byte[] {1});

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get(HeadshotCache.MAX_ENTRIES)).isNotEmpty();
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
