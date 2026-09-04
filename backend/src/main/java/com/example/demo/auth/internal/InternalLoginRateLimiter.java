package com.example.demo.auth.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Small in-memory brake against password guessing on the single review account. */
@Component
class InternalLoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, FailureWindow> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    InternalLoginRateLimiter() {
        this(Clock.systemUTC());
    }

    InternalLoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    boolean isBlocked(String key) {
        Instant now = clock.instant();
        FailureWindow window = failures.get(key);
        if (window == null) {
            return false;
        }
        if (!now.isBefore(window.startedAt().plus(WINDOW))) {
            failures.remove(key, window);
            return false;
        }
        return window.count() >= MAX_FAILURES;
    }

    void recordFailure(String key) {
        Instant now = clock.instant();
        failures.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.startedAt().plus(WINDOW))) {
                return new FailureWindow(now, 1);
            }
            return new FailureWindow(current.startedAt(), current.count() + 1);
        });
    }

    void clear(String key) {
        failures.remove(key);
    }

    private record FailureWindow(Instant startedAt, int count) {
    }
}
