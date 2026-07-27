package com.example.demo.club.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.club.model.Club;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// member_count is derived from club_member (see ClubMapper.xml's BaseColumnList correlated
// subquery) and update()/insert() intentionally no longer write it. This gets its own named
// in-memory database (same reasoning as ClubMapperTest) rather than sharing one with the other
// club mapper tests running in this JVM.
@SpringBootTest(properties = "spring.datasource.url="
    + "jdbc:h2:mem:club_mapper_update_member_count_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class ClubMapperUpdateMemberCountTest {

    @Autowired
    private ClubMapper clubMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS club_member");
        jdbcTemplate.execute("DROP TABLE IF EXISTS club_social_medias");
        jdbcTemplate.execute("DROP TABLE IF EXISTS clubs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS oauth_users");
        jdbcTemplate.execute("""
            CREATE TABLE oauth_users (
                uid BIGINT PRIMARY KEY AUTO_INCREMENT,
                provider VARCHAR(50) NOT NULL,
                provider_user_id VARCHAR(150) NOT NULL,
                email VARCHAR(255),
                display_name VARCHAR(255),
                avatar_url VARCHAR(500),
                role VARCHAR(50)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE clubs (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(150) NOT NULL,
                slug VARCHAR(160),
                alias_name VARCHAR(150),
                description CLOB,
                category VARCHAR(150),
                meeting_schedule VARCHAR(150),
                schedule_note CLOB,
                location VARCHAR(150),
                contact_email VARCHAR(150),
                advisor VARCHAR(150),
                image_url VARCHAR(300),
                member_count INT NOT NULL DEFAULT 0,
                achievements CLOB NOT NULL DEFAULT '[]',
                status VARCHAR(30) NOT NULL DEFAULT 'active',
                visibility VARCHAR(30) NOT NULL DEFAULT 'public',
                approved_at TIMESTAMP NULL,
                approved_by_oauth_user_id BIGINT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE club_social_medias (
                club_id BIGINT NOT NULL,
                social_type VARCHAR(50) NOT NULL,
                link_name VARCHAR(150) NOT NULL,
                link_url VARCHAR(500) NOT NULL,
                PRIMARY KEY (club_id, link_name)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE club_member (
                club_id BIGINT NOT NULL,
                oauth_user_id BIGINT NOT NULL,
                role_name VARCHAR(120) NOT NULL DEFAULT 'member',
                joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (club_id, oauth_user_id)
            )
            """);
    }

    @Test
    void updateIgnoresAClientSuppliedMemberCountInsteadOfOverwritingTheDerivedValue() {
        jdbcTemplate.update(
            "INSERT INTO clubs (id, name, category, member_count, status) "
                + "VALUES (1, 'Chess Club', 'Competition & Strategy', 0, 'active')");
        jdbcTemplate.update("INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (10, 'google', 'g-10')");
        jdbcTemplate.update("INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (11, 'google', 'g-11')");
        jdbcTemplate.update("INSERT INTO club_member (club_id, oauth_user_id) VALUES (1, 10)");
        jdbcTemplate.update("INSERT INTO club_member (club_id, oauth_user_id) VALUES (1, 11)");

        Club update = clubMapper.findById(1L);
        update.setName("Chess Club (renamed)");
        // Simulates a stale/forged client payload trying to set the count directly;
        // update() must not let this reach the member_count column at all.
        update.setMemberCount(999);

        clubMapper.update(update);

        Club reloaded = clubMapper.findById(1L);
        assertThat(reloaded.getName()).isEqualTo("Chess Club (renamed)");
        assertThat(reloaded.getMemberCount()).isEqualTo(2);

        Integer rawColumnValue = jdbcTemplate.queryForObject(
            "SELECT member_count FROM clubs WHERE id = 1", Integer.class);
        assertThat(rawColumnValue).isEqualTo(0);
    }

    @Test
    void insertIgnoresAClientSuppliedMemberCountAndLeavesTheColumnAtItsDefault() {
        Club club = new Club();
        club.setName("Robotics");
        club.setCategory("STEM & Innovation");
        club.setStatus("active");
        club.setVisibility("public");
        club.setAchievements(List.of());
        // A brand-new club has no members yet regardless of what a client sends here.
        club.setMemberCount(500);

        clubMapper.insert(club);

        Integer rawColumnValue = jdbcTemplate.queryForObject(
            "SELECT member_count FROM clubs WHERE id = " + club.getId(), Integer.class);
        assertThat(rawColumnValue).isEqualTo(0);
        assertThat(clubMapper.findById(club.getId()).getMemberCount()).isEqualTo(0);
    }
}
