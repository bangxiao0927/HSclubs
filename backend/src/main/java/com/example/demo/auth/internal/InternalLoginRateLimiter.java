package com.example.demo.auth.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * The brake on password guessing against the one optional review account.
 *
 * <p>Two counters, because the per-client one cannot be trusted on its own. Its key is the
 * address the application sees, and with {@code server.forward-headers-strategy: framework} that
 * address comes from {@code X-Forwarded-For} -- so on a deployment whose proxy appends to a
 * client-supplied value rather than replacing it, the caller chooses their own key and can reset
 * the per-client counter at will (see the reverse-proxy requirements in docs/DEPLOYMENT.md).
 * The second counter is not keyed at all: it caps failures against the account as a whole, so a
 * misconfigured proxy degrades this from "per-client throttle" to "account-wide throttle"
 * instead of to nothing.
 *
 * <p>The account-wide cap is deliberately several times the per-client one. It is a backstop
 * against unlimited guessing, not the first thing a wrong password should hit -- set equal to
 * the per-client limit it would let anyone lock the reviewer out with five requests, and a
 * lockout during App Store review reads as a broken app. A successful sign-in clears both, so
 * the person who actually holds the password always recovers immediately; nobody else can.
 */
@Component
class InternalLoginRateLimiter {

    private static final int MAX_FAILURES_PER_CLIENT = 5;
    private static final int MAX_FAILURES_ACCOUNT_WIDE = 20;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    /**
     * Cap on distinct client keys held at once. Without it the map is an unbounded, remotely
     * grown structure: one entry per address, evicted only when that same address is seen again,
     * which on the misconfigured-proxy case above means one entry per attacker-chosen string.
     * Expired entries are swept first; if the table is still full, a new key simply is not
     * tracked and the account-wide counter covers it.
     */
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final ConcurrentHashMap<String, FailureWindow> failures = new ConcurrentHashMap<>();
    private final AtomicReference<FailureWindow> accountWideFailures = new AtomicReference<>();
    private final Clock clock;

    InternalLoginRateLimiter() {
        this(Clock.systemUTC());
    }

    InternalLoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    boolean isBlocked(String key) {
        Instant now = clock.instant();
        if (countWithin(now, accountWideFailures.get()) >= MAX_FAILURES_ACCOUNT_WIDE) {
            return true;
        }
        FailureWindow window = failures.get(key);
        if (window == null) {
            return false;
        }
        if (hasExpired(now, window)) {
            failures.remove(key, window);
            return false;
        }
        return window.count() >= MAX_FAILURES_PER_CLIENT;
    }

    void recordFailure(String key) {
        Instant now = clock.instant();
        accountWideFailures.updateAndGet(current -> extend(current, now));

        if (failures.size() >= MAX_TRACKED_CLIENTS) {
            failures.values().removeIf(window -> hasExpired(now, window));
            if (failures.size() >= MAX_TRACKED_CLIENTS && !failures.containsKey(key)) {
                return;
            }
        }
        failures.compute(key, (ignored, current) -> extend(current, now));
    }

    /** Called only after the correct password, which is why clearing the account-wide count is safe. */
    void clear(String key) {
        failures.remove(key);
        accountWideFailures.set(null);
    }

    /** Visible for tests: how many client keys are currently held. */
    int trackedClients() {
        return failures.size();
    }

    private static FailureWindow extend(FailureWindow current, Instant now) {
        if (current == null || hasExpired(now, current)) {
            return new FailureWindow(now, 1);
        }
        return new FailureWindow(current.startedAt(), current.count() + 1);
    }

    private static int countWithin(Instant now, FailureWindow window) {
        return (window == null || hasExpired(now, window)) ? 0 : window.count();
    }

    private static boolean hasExpired(Instant now, FailureWindow window) {
        return !now.isBefore(window.startedAt().plus(WINDOW));
    }

    private record FailureWindow(Instant startedAt, int count) {
    }
}
