package com.example.demo.summary.service;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.summary.model.ClubSummaryProjection;
import com.example.demo.summary.model.SummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Builds the public school summary the future aggregator repo consumes.
 *
 * <p>Two things keep this endpoint cheap, because it is unauthenticated and therefore the
 * easiest thing on the site to hammer: it reads a narrow projection rather than whole club rows
 * (no description, no {@code achievements} CLOB), and the assembled response is cached for a
 * short TTL so a burst of requests collapses onto one query. The TTL is small enough that the
 * aggregator still sees a change promptly.
 */
@Service
public class SummaryService {

    private final ClubMapper clubMapper;
    private final String schoolName;
    private final String shortName;
    private final String slug;
    private final Duration cacheTtl;
    private final AtomicReference<CachedSummary> cached = new AtomicReference<>();
    private final Object refreshLock = new Object();

    public SummaryService(ClubMapper clubMapper,
                          @Value("${app.summary.school-name:HS Clubs}") String schoolName,
                          @Value("${app.summary.short-name:HS Clubs}") String shortName,
                          @Value("${app.summary.slug:hsclubs}") String slug,
                          @Value("${app.summary.cache-ttl-ms:60000}") long cacheTtlMillis) {
        this.clubMapper = clubMapper;
        this.schoolName = schoolName;
        this.shortName = shortName;
        this.slug = slug;
        this.cacheTtl = Duration.ofMillis(Math.max(0, cacheTtlMillis));
    }

    public SummaryResponse buildSummary() {
        CachedSummary snapshot = cached.get();
        if (snapshot != null && !snapshot.isExpired(cacheTtl)) {
            return snapshot.summary();
        }
        // Single-flight: without this, every request in flight when the TTL expires (or on a
        // cold start) runs its own query, which is exactly the burst the cache exists to
        // absorb -- and this endpoint is unauthenticated, so the burst size is whatever the
        // servlet pool allows. The second checked read inside the lock is what makes the
        // losers reuse the winner's result instead of repeating it.
        synchronized (refreshLock) {
            CachedSummary current = cached.get();
            if (current != null && !current.isExpired(cacheTtl)) {
                return current.summary();
            }
            SummaryResponse summary = computeSummary();
            cached.set(new CachedSummary(summary, System.nanoTime()));
            return summary;
        }
    }

    private SummaryResponse computeSummary() {
        List<ClubSummaryProjection> clubs = clubMapper.findSummaryProjections();

        Map<String, Integer> categoryCounts = clubs.stream()
            .filter(c -> c.getCategory() != null && !c.getCategory().isBlank())
            .collect(Collectors.groupingBy(
                ClubSummaryProjection::getCategory,
                LinkedHashMap::new,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));

        int totalMembers = clubs.stream()
            .mapToInt(c -> c.getMemberCount() != null ? c.getMemberCount() : 0)
            .sum();

        LocalDateTime lastUpdated = clubs.stream()
            .map(ClubSummaryProjection::getUpdatedAt)
            .filter(t -> t != null)
            .max(LocalDateTime::compareTo)
            .orElse(null);

        SummaryResponse summary = new SummaryResponse();
        summary.setSchoolName(schoolName);
        summary.setShortName(shortName);
        summary.setSlug(slug);
        summary.setStatus("active");
        summary.setClubCount(clubs.size());
        summary.setCategories(categoryCounts);
        summary.setMemberCount(totalMembers);
        summary.setLastUpdatedAt(lastUpdated);
        summary.setDataHash(computeHash(clubs));

        return summary;
    }

    /**
     * Compute a deterministic SHA-256 hash from club identities.
     * The 2nd repo can compare this hash to detect changes without
     * re-fetching all club data.
     */
    private String computeHash(List<ClubSummaryProjection> clubs) {
        String payload = clubs.stream()
            .sorted((a, b) -> Long.compare(a.getId() != null ? a.getId() : 0,
                                           b.getId() != null ? b.getId() : 0))
            .map(c -> (c.getId() != null ? c.getId() : "") + "|"
                    + (c.getName() != null ? c.getName() : "") + "|"
                    + (c.getCategory() != null ? c.getCategory() : "") + "|"
                    + (c.getMemberCount() != null ? c.getMemberCount() : 0))
            .collect(Collectors.joining("\n"));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private record CachedSummary(SummaryResponse summary, long builtAtNanos) {
        boolean isExpired(Duration ttl) {
            return System.nanoTime() - builtAtNanos >= ttl.toNanos();
        }
    }
}
