package com.example.demo.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class InternalLoginRateLimiterTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    /** A clock the test moves by hand, so the 15-minute window can be crossed without waiting. */
    private static final class MovableClock extends Clock {
        private Instant now = START;

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    private static void fail(InternalLoginRateLimiter limiter, String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(key);
        }
    }

    @Test
    void blocksOneClientAfterFiveFailures() {
        InternalLoginRateLimiter limiter = new InternalLoginRateLimiter(new MovableClock());

        fail(limiter, "10.0.0.1", 4);
        assertThat(limiter.isBlocked("10.0.0.1")).isFalse();

        limiter.recordFailure("10.0.0.1");
        assertThat(limiter.isBlocked("10.0.0.1")).isTrue();
        // Another client is unaffected: one attacker must not lock the reviewer out.
        assertThat(limiter.isBlocked("10.0.0.2")).isFalse();
    }

    // The block has to lift on its own. A permanent lockout would be indistinguishable from a
    // broken sign-in for whoever hits it, and nothing here ever expires it except time.
    @Test
    void theBlockLiftsOnceTheWindowPasses() {
        MovableClock clock = new MovableClock();
        InternalLoginRateLimiter limiter = new InternalLoginRateLimiter(clock);
        fail(limiter, "10.0.0.1", 5);

        clock.advance(Duration.ofMinutes(14));
        assertThat(limiter.isBlocked("10.0.0.1")).isTrue();

        clock.advance(Duration.ofMinutes(2));
        assertThat(limiter.isBlocked("10.0.0.1")).isFalse();
    }

    @Test
    void aSuccessfulSignInClearsTheFailuresItFollows() {
        InternalLoginRateLimiter limiter = new InternalLoginRateLimiter(new MovableClock());
        fail(limiter, "10.0.0.1", 5);

        limiter.clear("10.0.0.1");

        assertThat(limiter.isBlocked("10.0.0.1")).isFalse();
    }

    // The point of the account-wide count: a caller who can choose their own key (a proxy that
    // appends a client-supplied X-Forwarded-For) escapes the per-client counter entirely, so
    // guessing must still run into a ceiling.
    @Test
    void rotatingTheKeyStillRunsIntoTheAccountWideCap() {
        InternalLoginRateLimiter limiter = new InternalLoginRateLimiter(new MovableClock());

        for (int i = 0; i < 20; i++) {
            String freshKey = "198.51.100." + i;
            assertThat(limiter.isBlocked(freshKey)).isFalse();
            limiter.recordFailure(freshKey);
        }

        assertThat(limiter.isBlocked("198.51.100.999")).isTrue();
    }

    // ...and that ceiling also lifts with time, and is cleared by the person who knows the
    // password, so it cannot be used to lock a reviewer out for good.
    @Test
    void theAccountWideCapIsNotAPermanentLockout() {
        MovableClock clock = new MovableClock();
        InternalLoginRateLimiter limiter = new InternalLoginRateLimiter(clock);
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure("198.51.100." + i);
        }
        assertThat(limiter.isBlocked("10.0.0.9")).isTrue();

        clock.advance(Duration.ofMinutes(16));
        assertThat(limiter.isBlocked("10.0.0.9")).isFalse();

        for (int i = 0; i < 20; i++) {
            limiter.recordFailure("203.0.113." + i);
        }
        assertThat(limiter.isBlocked("10.0.0.9")).isTrue();
        limiter.clear("10.0.0.9");
        assertThat(limiter.isBlocked("10.0.0.9")).isFalse();
    }

    // The table is remotely grown, one entry per key, so it needs a ceiling of its own.
    @Test
    void theTrackedClientTableStaysBounded() {
        InternalLoginRateLimiter limiter = new InternalLoginRateLimiter(new MovableClock());

        for (int i = 0; i < 25_000; i++) {
            limiter.recordFailure("key-" + i);
        }

        assertThat(limiter.trackedClients()).isLessThanOrEqualTo(10_000);
    }

    // Expired entries are what the sweep reclaims: the same load spread across two windows must
    // not accumulate.
    @Test
    void expiredEntriesAreReclaimedRatherThanAccumulating() {
        MovableClock clock = new MovableClock();
        InternalLoginRateLimiter limiter = new InternalLoginRateLimiter(clock);
        for (int i = 0; i < 10_000; i++) {
            limiter.recordFailure("first-window-" + i);
        }

        clock.advance(Duration.ofMinutes(16));
        limiter.recordFailure("second-window");

        assertThat(limiter.trackedClients()).isLessThan(10_000);
    }
}
