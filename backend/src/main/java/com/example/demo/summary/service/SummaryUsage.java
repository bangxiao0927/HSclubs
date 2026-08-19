package com.example.demo.summary.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Counts reads of the summary endpoints during the legacy observation window (issue
 * hsclubs-guiding-page#40).
 *
 * <p>The point is to answer "is the unversioned {@code /api/summary} still being read?" before it
 * is ever retired, and to see v1 adoption rise beside it. It records only a per-route count and
 * the last time each was seen -- never who read it, from where, or with what -- because a read of
 * a public, anonymous endpoint carries no user data and this metric must not invent any.
 *
 * <p>In-memory: a restart resets the counts, which is acceptable for a coarse adoption signal.
 * Retiring the legacy endpoint is a written decision made on this evidence, not an automated one.
 */
@Component
public class SummaryUsage {

    public static final String LEGACY = "legacy-summary";
    public static final String V1 = "v1-summary";

    private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();

    public void record(String route) {
        counts.computeIfAbsent(route, key -> new AtomicLong()).incrementAndGet();
    }

    public long count(String route) {
        AtomicLong value = counts.get(route);
        return value == null ? 0L : value.get();
    }
}
