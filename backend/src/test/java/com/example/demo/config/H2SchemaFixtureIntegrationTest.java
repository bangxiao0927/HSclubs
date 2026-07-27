package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the h2 Spring profile's schema fixture is actually runnable, not just idempotent on
 * paper: activates the real "h2" profile (application-h2.yaml), lets it run db/h2/reset.sql +
 * schema.sql + db/h2/data.sql exactly as local dev would, and round-trips achievements through
 * AchievementsTypeHandler against the real clubs table it creates. This is also the executable
 * proof behind schema.sql's file-header claim that achievements has to stay CLOB for H2 (a real
 * JSON column here would fail this exact round trip -- see that header for why).
 */
@SpringBootTest
@ActiveProfiles("h2")
@TestPropertySource(properties =
    "spring.datasource.url=jdbc:h2:mem:h2_schema_fixture_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class H2SchemaFixtureIntegrationTest {

    @Autowired
    private ClubMapper clubMapper;

    @Test
    void achievementsRoundTripThroughTheRealH2ProfileSchema() {
        Club club = new Club();
        club.setName("Schema Fixture Test Club");
        club.setMemberCount(0);
        club.setStatus("active");
        club.setVisibility("public");
        club.setAchievements(List.of("a", "b"));

        clubMapper.insert(club);
        Club reloaded = clubMapper.findById(club.getId());

        assertThat(reloaded.getAchievements()).containsExactly("a", "b");
    }
}
