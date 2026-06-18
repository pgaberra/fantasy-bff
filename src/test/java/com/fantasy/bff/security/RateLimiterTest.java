package com.fantasy.bff.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant start) {
            this.instant = start;
        }

        private void advance(Duration amount) {
            this.instant = this.instant.plus(amount);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void allowsUpToLimitThenBlocks() {
        RateLimiter limiter = new RateLimiter(new MutableClock(Instant.parse("2026-06-18T00:00:00Z")));

        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isFalse();
    }

    @Test
    void resetsAfterWindowElapses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-18T00:00:00Z"));
        RateLimiter limiter = new RateLimiter(clock);

        assertThat(limiter.tryAcquire("k", 1, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 1, WINDOW)).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("k", 1, WINDOW)).isTrue();
    }

    @Test
    void tracksKeysIndependently() {
        RateLimiter limiter = new RateLimiter(new MutableClock(Instant.parse("2026-06-18T00:00:00Z")));

        assertThat(limiter.tryAcquire("a", 1, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("a", 1, WINDOW)).isFalse();
        assertThat(limiter.tryAcquire("b", 1, WINDOW)).isTrue();
    }
}
