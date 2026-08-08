package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Normalizes stored room labels once at startup.
 *
 * <p>Deliberately not transactional and deliberately per-row fault tolerant: Spring Boot
 * propagates anything an {@link ApplicationRunner} throws and closes the context, so a single
 * unrepairable row (a normalized value longer than {@code location VARCHAR(150)}, say) used to
 * take the whole site down -- and keep taking it down on every restart, because the offending
 * row is never fixed. A best-effort cleanup job must never be able to do that: a row that fails
 * is logged and skipped, and the app starts.
 */
@Component
@ConditionalOnProperty(
    name = "app.club-location-repair.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ClubLocationRepairRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClubLocationRepairRunner.class);

    private final ClubMapper clubMapper;

    public ClubLocationRepairRunner(ClubMapper clubMapper) {
        this.clubMapper = clubMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        int repaired = 0;
        int failed = 0;
        for (Club club : clubMapper.findAllLocations()) {
            String normalized = ClubLocationNormalizer.normalize(club.getLocation());
            if (Objects.equals(normalized, club.getLocation())) {
                continue;
            }
            try {
                repaired += clubMapper.updateLocation(club.getId(), normalized);
            } catch (RuntimeException e) {
                failed++;
                LOGGER.warn("Skipped normalizing the location of club {}: {}", club.getId(), e.toString());
            }
        }
        if (repaired > 0) {
            LOGGER.info("Normalized room labels for {} club location(s)", repaired);
        }
        if (failed > 0) {
            LOGGER.warn("Could not normalize {} club location(s); the application started anyway", failed);
        }
    }
}
