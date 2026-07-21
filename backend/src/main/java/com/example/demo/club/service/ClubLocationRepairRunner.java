package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

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
    @Transactional
    public void run(ApplicationArguments args) {
        int repaired = 0;
        for (Club club : clubMapper.findAllLocations()) {
            String normalized = ClubLocationNormalizer.normalize(club.getLocation());
            if (!Objects.equals(normalized, club.getLocation())) {
                repaired += clubMapper.updateLocation(club.getId(), normalized);
            }
        }
        if (repaired > 0) {
            LOGGER.info("Normalized room labels for {} club location(s)", repaired);
        }
    }
}
